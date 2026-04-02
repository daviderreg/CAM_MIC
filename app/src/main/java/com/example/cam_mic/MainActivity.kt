package com.example.cam_mic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cam_mic.data.UsageEventRepository
import com.example.cam_mic.data.getUsageEventRepository
import com.example.cam_mic.databinding.ActivityMainBinding
import com.example.cam_mic.export.UsageEventExporter
import com.example.cam_mic.service.MonitoringService
import com.example.cam_mic.ui.UsageEventAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main activity for the Camera & Microphone Monitoring app.
 * 
 * Handles:
 * - Runtime permission requests (RECORD_AUDIO, CAMERA, POST_NOTIFICATIONS)
 * - Usage access permission (PACKAGE_USAGE_STATS) via Settings
 * - Starting/stopping the MonitoringService
 * - Displaying recorded events in a RecyclerView
 * - Exporting events to CSV/JSON
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: UsageEventRepository
    private lateinit var adapter: UsageEventAdapter
    private lateinit var exporter: UsageEventExporter

    private var isServiceRunning = false

    // Permission launcher for runtime permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val notificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true // Not required on Android 12-

        if (audioGranted && cameraGranted && notificationsGranted) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions denied. Monitoring may not work properly.", Toast.LENGTH_LONG).show()
        }
        updateServiceStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize repository and exporter
        repository = applicationContext.getUsageEventRepository()
        exporter = UsageEventExporter(this)

        // Setup RecyclerView
        setupRecyclerView()

        // Setup button listeners
        setupButtonListeners()

        // Check and request permissions
        checkAndRequestPermissions()

        // Observe events
        observeEvents()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupRecyclerView() {
        adapter = UsageEventAdapter { event ->
            // Optional: Show event details on click
            showEventDetails(event)
        }
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter
    }

    private fun setupButtonListeners() {
        // Toggle service button
        binding.btnToggleService.setOnClickListener {
            if (isServiceRunning) {
                stopMonitoringService()
            } else {
                startMonitoringService()
            }
        }

        // Usage access button
        binding.btnUsageAccess.setOnClickListener {
            openUsageAccessSettings()
        }

        // Export FAB
        binding.fabExport.setOnClickListener {
            showExportDialog()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Check RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        // Check CAMERA
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        // Check POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun hasUsageAccessPermission(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun startMonitoringService() {
        if (!hasUsageAccessPermission()) {
            AlertDialog.Builder(this)
                .setTitle("Usage Access Required")
                .setMessage("This app needs Usage Access permission to detect which app is using the camera. Would you like to grant it now?")
                .setPositiveButton("Open Settings") { _, _ ->
                    openUsageAccessSettings()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val intent = Intent(this, MonitoringService::class.java)
        startForegroundService(intent)
        updateServiceStatus()
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        intent.action = MonitoringService.ACTION_STOP_SERVICE
        startService(intent)
        updateServiceStatus()
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceStatus() {
        // Check if service is actually running
        isServiceRunning = isServiceRunning()

        if (isServiceRunning) {
            binding.tvServiceStatus.text = "Running"
            binding.tvServiceStatus.setTextColor(getColor(R.color.status_active))
            binding.btnToggleService.text = "Stop Monitoring"
        } else {
            binding.tvServiceStatus.text = "Stopped"
            binding.tvServiceStatus.setTextColor(getColor(R.color.status_inactive))
            binding.btnToggleService.text = "Start Monitoring"
        }

        // Update usage access button visibility
        val hasUsageAccess = hasUsageAccessPermission()
        binding.btnUsageAccess.visibility = if (hasUsageAccess) View.GONE else View.VISIBLE
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        val services = manager.getRunningServices(Int.MAX_VALUE)
        for (service in services) {
            if (MonitoringService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repository.getAllEvents().collectLatest { events ->
                adapter.submitList(events)
                binding.tvEventsCount.text = "${events.size} event${if (events.size != 1) "s" else ""}"
                
                // Show/hide empty state
                binding.layoutEmptyState.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                binding.rvEvents.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showExportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Export Events")
            .setMessage("Choose export format:")
            .setPositiveButton("CSV") { _, _ ->
                exportEvents(UsageEventExporter.ExportFormat.CSV)
            }
            .setNegativeButton("JSON") { _, _ ->
                exportEvents(UsageEventExporter.ExportFormat.JSON)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun exportEvents(format: UsageEventExporter.ExportFormat) {
        lifecycleScope.launch {
            try {
                val events = repository.getAllEventsOnce()
                if (events.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No events to export", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val uri = exporter.export(events, format)
                val shareIntent = exporter.createShareIntent(uri, format)
                startActivity(Intent.createChooser(shareIntent, "Share exported data"))
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Export failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showEventDetails(event: com.example.cam_mic.data.UsageEventEntity) {
        val resourceInfo = if (event.cameraId != null) {
            "Camera ${event.cameraId}"
        } else {
            "Microphone"
        }

        AlertDialog.Builder(this)
            .setTitle("Event Details")
            .setMessage(
                "Package: ${event.packageName}\n" +
                "Type: ${event.resourceType} ($resourceInfo)\n" +
                "Start: ${event.startTimeMs}\n" +
                "End: ${event.endTimeMs ?: "Ongoing"}\n" +
                "Duration: ${event.durationMs}ms"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
