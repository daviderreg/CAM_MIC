package com.example.cam_mic.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.app.KeyguardManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.cam_mic.MainActivity
import com.example.cam_mic.R
import com.example.cam_mic.data.ResourceType
import com.example.cam_mic.data.UsageEventEntity
import com.example.cam_mic.data.UsageEventRepository
import com.example.cam_mic.data.getUsageEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Service that monitors microphone and camera usage by all apps.
 * 
 * Audio Monitoring: Uses AudioManager.AudioRecordingCallback to get precise
 * package names from AudioRecordingConfiguration.
 * 
 * Camera Monitoring: Uses CameraManager.AvailabilityCallback for camera state.
 * Package name is deduced using UsageStatsManager (see note below).
 * 
 * NOTE ON CAMERA PACKAGE DEDUCTION:
 * The CameraManager.AvailabilityCallback does NOT provide the package name of
 * the app using the camera. We deduce it by querying UsageStatsManager for the
 * most recently used app at the time of camera activation. This approach has limitations:
 * - May not be accurate if multiple apps access camera rapidly
 * - System processes may not appear in UsageStats
 * - Background app switching can cause false attribution
 * - The deduced package name is an approximation, not a guarantee
 */
class MonitoringService : Service() {

    companion object {
        private const val TAG = "MonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "monitoring_channel"
        
        // Action for stopping the service
        const val ACTION_STOP_SERVICE = "com.example.cam_mic.action.STOP_SERVICE"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: UsageEventRepository
    
    // Audio monitoring
    private var audioManager: AudioManager? = null
    private val audioRecordingCallback = AudioRecordingCallbackImpl()
    
    // Camera monitoring
    private var cameraManager: CameraManager? = null
    private val cameraAvailabilityCallback = CameraAvailabilityCallbackImpl()
    
    // Usage stats manager for package deduction (camera)
    private var usageStatsManager: UsageStatsManager? = null
    
    // Activity manager for running processes
    private var activityManager: ActivityManager? = null
    
    // Power manager for screen state detection
    private var powerManager: PowerManager? = null
    
    // Keyguard manager for lock state detection
    private var keyguardManager: KeyguardManager? = null
    
    // Handler for debouncing rapid camera events
    private val handler = Handler(Looper.getMainLooper())
    
    // Track ongoing events to properly calculate duration
    // Key: unique identifier (packageName_resourceType_cameraId)
    private val ongoingEvents = mutableMapOf<String, OngoingEvent>()
    
    data class OngoingEvent(
        val startTimeMs: Long,
        val packageName: String,
        val resourceType: ResourceType,
        val cameraId: String? = null
    )
    
    private fun createEventKey(packageName: String, resourceType: ResourceType, cameraId: String? = null): String {
        return "${packageName}_${resourceType}_${cameraId ?: "default"}"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        repository = applicationContext.getUsageEventRepository()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with intent: ${intent?.action}")
        
        // Handle stop action from notification
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Stopping service from notification action")
            stopMonitoring()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Create notification channel first
        createNotificationChannel()
        
        // Start foreground with appropriate service type for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
            Log.d(TAG, "Started foreground service with type microphone|camera (API 34+)")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
            Log.d(TAG, "Started foreground service with type (API 29-33)")
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Started foreground service (legacy)")
        }
        
        startMonitoring()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopMonitoring()
        serviceScope.cancel()
        
        // Close any ongoing events
        serviceScope.launch {
            closeAllOngoingEvents()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoring Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when microphone or camera is being used"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, MonitoringService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitoring Active")
            .setContentText("Tracking microphone and camera usage")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun startMonitoring() {
        Log.d(TAG, "Starting monitoring")
        
        // Check permissions before registering callbacks
        val audioPermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        val cameraPermission = checkSelfPermission(android.Manifest.permission.CAMERA)
        Log.d(TAG, "RECORD_AUDIO permission: $audioPermission")
        Log.d(TAG, "CAMERA permission: $cameraPermission")
        
        // Register audio recording callback
        try {
            audioManager?.registerAudioRecordingCallback(audioRecordingCallback, handler)
            Log.d(TAG, "Audio recording callback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register audio callback", e)
        }
        
        // Register camera availability callback
        try {
            cameraManager?.registerAvailabilityCallback(cameraAvailabilityCallback, handler)
            Log.d(TAG, "Camera availability callback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register camera callback", e)
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stopping monitoring")
        
        // Unregister audio callback
        runCatching {
            audioManager?.unregisterAudioRecordingCallback(audioRecordingCallback)
        }
        
        // Unregister camera callback
        runCatching {
            cameraManager?.unregisterAvailabilityCallback(cameraAvailabilityCallback)
        }
    }

    /**
     * Save a usage event to the database.
     */
    private fun saveEvent(event: UsageEventEntity) {
        serviceScope.launch {
            try {
                repository.insert(event)
                Log.d(TAG, "Event saved: ${event.packageName} - ${event.resourceType}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving event", e)
            }
        }
    }

    /**
     * Close all ongoing events (called when service stops).
     */
    private suspend fun closeAllOngoingEvents() {
        val endTimeMs = System.currentTimeMillis()
        ongoingEvents.values.forEach { ongoing ->
            val durationMs = endTimeMs - ongoing.startTimeMs
            val event = UsageEventEntity(
                startTimeMs = ongoing.startTimeMs,
                endTimeMs = endTimeMs,
                durationMs = durationMs,
                packageName = ongoing.packageName,
                resourceType = ongoing.resourceType,
                cameraId = ongoing.cameraId
            )
            repository.insert(event)
        }
        ongoingEvents.clear()
    }

    /**
     * Deduce the package name using a safe, multi-strategy approach:
     *
     * Strategy 1: UsageStatsManager for ACTIVITY_RESUMED events (most accurate)
     * Strategy 2: ActivityManager.getRunningAppProcesses() for foreground processes
     * Strategy 3: Device Context Awareness - check screen/lock state
     *
     * SECURITY NOTE: We do NOT use vague fallbacks like "last used app" because
     * they can create false positives. For example, if Face Unlock activates the
     * camera with screen off, we must NOT blame the last app used hours ago.
     *
     * @return Package name, "System (Locked/Screen Off)", or "Unknown Background Process"
     */
    private fun deducePackageNameFromUsageStats(): String {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 5000 // Look back 5 seconds
        
        // Strategy 1: Try UsageStatsManager for ACTIVITY_RESUMED events (most reliable)
        val fromActivityResumed = try {
            val usageEvents = usageStatsManager?.queryEvents(startTime, endTime)
            val eventList = mutableListOf<UsageEvents.Event>()
            
            usageEvents?.let { events ->
                while (events.hasNextEvent()) {
                    val event = UsageEvents.Event()
                    if (events.getNextEvent(event)) {
                        eventList.add(event)
                    }
                }
            }
            
            eventList
                .filter { it.packageName != packageName } // Exclude ourselves
                .filter { it.eventType == UsageEvents.Event.ACTIVITY_RESUMED }
                .maxByOrNull { it.timeStamp }?.packageName
        } catch (e: Exception) {
            Log.w(TAG, "Error querying usage events", e)
            null
        }
        
        if (!fromActivityResumed.isNullOrEmpty()) {
            Log.d(TAG, "Deduced package from ACTIVITY_RESUMED: $fromActivityResumed")
            return fromActivityResumed
        }
        
        // Strategy 2: Try ActivityManager for running foreground processes
        val fromRunningProcesses = try {
            @Suppress("DEPRECATION")
            val runningProcesses = activityManager?.getRunningAppProcesses()
            runningProcesses
                ?.filter { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                ?.filter { it.processName != packageName } // Exclude ourselves
                ?.maxByOrNull { it.pid }?.processName
        } catch (e: Exception) {
            Log.w(TAG, "Error getting running processes", e)
            null
        }
        
        if (!fromRunningProcesses.isNullOrEmpty()) {
            Log.d(TAG, "Deduced package from running processes: $fromRunningProcesses")
            return fromRunningProcesses
        }
        
        // Strategy 3: Device Context Awareness - check screen and lock state
        // This prevents false positives when camera/mic is used by system processes
        // (e.g., Face Unlock, voice assistant) with screen off or device locked
        val isScreenOff = !(powerManager?.isInteractive ?: true)
        val isLocked = keyguardManager?.isKeyguardLocked ?: false
        
        return if (isScreenOff || isLocked) {
            // Screen off or device locked = likely a system process
            Log.d(TAG, "Device context: screenOff=$isScreenOff, locked=$isLocked -> System process")
            "System (Locked/Screen Off)"
        } else {
            // Screen on and unlocked, but no matching app found
            // Could be a background process or service
            Log.d(TAG, "Device context: screen on, unlocked, but no matching app -> Unknown background process")
            "Unknown Background Process"
        }
    }

    /**
     * Callback for audio recording events.
     * Provides accurate package names from AudioRecordingConfiguration.
     */
    private inner class AudioRecordingCallbackImpl : AudioManager.AudioRecordingCallback() {

        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            Log.d(TAG, "Audio recording config changed: ${configs.size} active recordings")

            val isMicInUse = configs.isNotEmpty()
            val eventKey = "microphone_global_event"

            if (isMicInUse) {
                // Se il microfono è in uso ma non lo stavamo tracciando, iniziamo!
                if (!ongoingEvents.containsKey(eventKey)) {
                    val pkgName = deducePackageNameFromUsageStats() // Deducibile come la fotocamera
                    ongoingEvents[eventKey] = OngoingEvent(
                        startTimeMs = System.currentTimeMillis(),
                        packageName = pkgName,
                        resourceType = ResourceType.AUDIO
                    )
                    Log.d(TAG, "Audio recording started by (deduced): $pkgName")
                }
            } else {
                // Se la lista è vuota, il microfono è stato rilasciato
                ongoingEvents.remove(eventKey)?.let { ongoing ->
                    val endTimeMs = System.currentTimeMillis()
                    val durationMs = endTimeMs - ongoing.startTimeMs

                    val event = UsageEventEntity(
                        startTimeMs = ongoing.startTimeMs,
                        endTimeMs = endTimeMs,
                        durationMs = durationMs,
                        packageName = ongoing.packageName,
                        resourceType = ResourceType.AUDIO
                    )
                    saveEvent(event)
                    Log.d(TAG, "Audio recording stopped by: ${ongoing.packageName}, duration: ${durationMs}ms")
                }
            }
        }
    }
    /**
     * Callback for camera availability events.
     * Note: Does not provide package name - must be deduced.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private inner class CameraAvailabilityCallbackImpl : CameraManager.AvailabilityCallback() {

        override fun onCameraAvailable(cameraId: String) {
            Log.d(TAG, "Camera $cameraId available (closed)")

            // La fotocamera è tornata disponibile, quindi è stata CHIUSA. Salviamo l'evento.
            val matchingKey = ongoingEvents.keys.find { key ->
                val event = ongoingEvents[key]
                event?.resourceType == ResourceType.CAMERA && event.cameraId == cameraId
            }

            matchingKey?.let { key ->
                val ongoing = ongoingEvents.remove(key) ?: return@let
                val endTimeMs = System.currentTimeMillis()
                val durationMs = endTimeMs - ongoing.startTimeMs

                val event = UsageEventEntity(
                    startTimeMs = ongoing.startTimeMs,
                    endTimeMs = endTimeMs,
                    durationMs = durationMs,
                    packageName = ongoing.packageName,
                    resourceType = ResourceType.CAMERA,
                    cameraId = cameraId
                )
                saveEvent(event)
                Log.d(TAG, "Camera $cameraId closed, duration: ${durationMs}ms")
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            Log.d(TAG, "Camera $cameraId unavailable (opened)")

            // La fotocamera è non disponibile, quindi qualcuno l'ha APERTA.
            val pkgName = deducePackageNameFromUsageStats()
            val eventKey = createEventKey(pkgName, ResourceType.CAMERA, cameraId)

            if (!ongoingEvents.containsKey(eventKey)) {
                ongoingEvents[eventKey] = OngoingEvent(
                    startTimeMs = System.currentTimeMillis(),
                    packageName = pkgName,
                    resourceType = ResourceType.CAMERA,
                    cameraId = cameraId
                )
                Log.d(TAG, "Camera $cameraId opened by (deduced): $pkgName")
            }
        }
    }
}
