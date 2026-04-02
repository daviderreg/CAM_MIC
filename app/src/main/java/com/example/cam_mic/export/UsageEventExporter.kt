package com.example.cam_mic.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.cam_mic.data.UsageEventEntity
import com.example.cam_mic.ui.formatDuration
import com.example.cam_mic.ui.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Utility class for exporting usage events to CSV or JSON format.
 */
class UsageEventExporter(private val context: Context) {

    /**
     * Export format options.
     */
    enum class ExportFormat {
        CSV,
        JSON
    }

    /**
     * Export events to the specified format and return the file URI for sharing.
     * @param events List of events to export
     * @param format Export format (CSV or JSON)
     * @return Uri of the exported file
     */
    suspend fun export(events: List<UsageEventEntity>, format: ExportFormat): Uri = withContext(Dispatchers.IO) {
        val file = createExportFile(format)
        writeToFile(file, events, format)
        getFileUri(file)
    }

    /**
     * Create a temporary file for export.
     */
    private fun createExportFile(format: ExportFormat): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
        val extension = when (format) {
            ExportFormat.CSV -> "csv"
            ExportFormat.JSON -> "json"
        }
        val filename = "usage_export_${timestamp}.$extension"
        return File(context.cacheDir, filename)
    }

    /**
     * Get a shareable URI for the file using FileProvider.
     */
    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Write events to the file in the specified format.
     */
    private fun writeToFile(file: File, events: List<UsageEventEntity>, format: ExportFormat) {
        when (format) {
            ExportFormat.CSV -> writeCsv(file, events)
            ExportFormat.JSON -> writeJson(file, events)
        }
    }

    /**
     * Write events to CSV format.
     */
    private fun writeCsv(file: File, events: List<UsageEventEntity>) {
        FileWriter(file).use { writer ->
            // Write header
            writer.appendLine("id,startTime,endTime,durationMs,durationFormatted,packageName,resourceType,cameraId")
            
            // Write data rows
            events.forEach { event ->
                val row = listOf(
                    event.id.toString(),
                    event.startTimeMs.formatTimestamp(),
                    event.endTimeMs?.formatTimestamp() ?: "Ongoing",
                    event.durationMs.toString(),
                    event.durationMs.formatDuration(),
                    escapeCsv(event.packageName),
                    event.resourceType.name,
                    event.cameraId ?: ""
                ).joinToString(",")
                writer.appendLine(row)
            }
        }
    }

    /**
     * Escape CSV values that contain commas or quotes.
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Write events to JSON format.
     */
    private fun writeJson(file: File, events: List<UsageEventEntity>) {
        FileWriter(file).use { writer ->
            writer.append("[\n")
            events.forEachIndexed { index, event ->
                val json = buildJsonEvent(event)
                writer.append(json)
                if (index < events.lastIndex) {
                    writer.append(",\n")
                } else {
                    writer.append("\n")
                }
            }
            writer.append("]\n")
        }
    }

    /**
     * Build JSON representation of a single event.
     */
    private fun buildJsonEvent(event: UsageEventEntity): String {
        return buildString {
            append("  {\n")
            append("    \"id\": ${event.id},\n")
            append("    \"startTime\": \"${event.startTimeMs.formatTimestamp()}\",\n")
            append("    \"startTimeMs\": ${event.startTimeMs},\n")
            append("    \"endTime\": ${if (event.endTimeMs != null) "\"${event.endTimeMs.formatTimestamp()}\"" else "null"},\n")
            append("    \"endTimeMs\": ${event.endTimeMs ?: "null"},\n")
            append("    \"durationMs\": ${event.durationMs},\n")
            append("    \"durationFormatted\": \"${event.durationMs.formatDuration()}\",\n")
            append("    \"packageName\": \"${event.packageName}\",\n")
            append("    \"resourceType\": \"${event.resourceType.name}\",\n")
            append("    \"cameraId\": ${if (event.cameraId != null) "\"${event.cameraId}\"" else "null"}\n")
            append("  }")
        }
    }

    /**
     * Create a share intent for the exported file.
     * @param fileUri URI of the exported file
     * @param format Export format (for MIME type)
     * @return Intent ready to be used with startActivity
     */
    fun createShareIntent(fileUri: Uri, format: ExportFormat): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = when (format) {
                ExportFormat.CSV -> "text/csv"
                ExportFormat.JSON -> "application/json"
            }
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

/**
 * Extension function to append a line to FileWriter.
 */
private fun FileWriter.appendLine(text: String) {
    write(text)
    write("\n")
}
