package com.example.cam_mic.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a single usage event of microphone or camera.
 * Each event tracks when an app started using a resource and when it stopped.
 * 
 * @property id Auto-generated primary key
 * @property startTimeMs Event start timestamp in milliseconds since epoch
 * @property endTimeMs Event end timestamp in milliseconds since epoch (null = ongoing)
 * @property durationMs Total duration in milliseconds
 * @property packageName Package name of the app using the resource
 * @property resourceType Type of resource being used (AUDIO or CAMERA)
 * @property cameraId Camera identifier (only for CAMERA events, e.g., "0", "1")
 */
@Entity(
    tableName = "usage_events",
    indices = [
        Index("package_name"),
        Index("resource_type"),
        Index("start_time_ms")
    ]
)
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,
    
    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long? = null,
    
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    
    @ColumnInfo(name = "package_name")
    val packageName: String,
    
    @ColumnInfo(name = "resource_type")
    val resourceType: ResourceType,
    
    @ColumnInfo(name = "camera_id")
    val cameraId: String? = null
)

/**
 * Enum representing the type of resource being monitored.
 */
enum class ResourceType {
    AUDIO,
    CAMERA
}
