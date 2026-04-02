package com.example.cam_mic.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for usage events.
 * Provides suspend functions and Flow for reactive database access.
 */
@Dao
interface UsageEventDao {
    
    /**
     * Insert a new usage event.
     * @param event The event to insert
     * @return The row ID of the inserted event
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: UsageEventEntity): Long
    
    /**
     * Insert multiple usage events.
     * @param events List of events to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<UsageEventEntity>)
    
    /**
     * Update an existing usage event.
     * @param event The event to update
     */
    @Update
    suspend fun update(event: UsageEventEntity)
    
    /**
     * Delete a usage event.
     * @param event The event to delete
     */
    @Delete
    suspend fun delete(event: UsageEventEntity)
    
    /**
     * Get all usage events ordered by start time (newest first).
     * @return Flow of all events
     */
    @Query("SELECT * FROM usage_events ORDER BY start_time_ms DESC")
    fun getAllEvents(): Flow<List<UsageEventEntity>>
    
    /**
     * Get all usage events as a simple list (one-time query).
     * @return List of all events
     */
    @Query("SELECT * FROM usage_events ORDER BY start_time_ms DESC")
    suspend fun getAllEventsOnce(): List<UsageEventEntity>
    
    /**
     * Get ongoing events (events with null end_time_ms).
     * @return List of ongoing events
     */
    @Query("SELECT * FROM usage_events WHERE end_time_ms IS NULL ORDER BY start_time_ms DESC")
    suspend fun getOngoingEvents(): List<UsageEventEntity>
    
    /**
     * Get events by package name.
     * @param packageName The package name to filter by
     * @return Flow of events for the specified package
     */
    @Query("SELECT * FROM usage_events WHERE package_name = :packageName ORDER BY start_time_ms DESC")
    fun getEventsByPackage(packageName: String): Flow<List<UsageEventEntity>>
    
    /**
     * Get events by resource type.
     * @param resourceType The resource type to filter by
     * @return Flow of events for the specified resource type
     */
    @Query("SELECT * FROM usage_events WHERE resource_type = :resourceType ORDER BY start_time_ms DESC")
    fun getEventsByResourceType(resourceType: ResourceType): Flow<List<UsageEventEntity>>
    
    /**
     * Get events within a date range.
     * @param startTimeMs Start timestamp in milliseconds
     * @param endTimeMs End timestamp in milliseconds
     * @return Flow of events within the date range
     */
    @Query("SELECT * FROM usage_events WHERE start_time_ms >= :startTimeMs AND start_time_ms <= :endTimeMs ORDER BY start_time_ms DESC")
    fun getEventsByDateRange(startTimeMs: Long, endTimeMs: Long): Flow<List<UsageEventEntity>>
    
    /**
     * Get events by package name and resource type.
     * @param packageName The package name to filter by
     * @param resourceType The resource type to filter by
     * @return Flow of events matching both criteria
     */
    @Query("SELECT * FROM usage_events WHERE package_name = :packageName AND resource_type = :resourceType ORDER BY start_time_ms DESC")
    fun getEventsByPackageAndResourceType(packageName: String, resourceType: ResourceType): Flow<List<UsageEventEntity>>
    
    /**
     * Delete all usage events.
     */
    @Query("DELETE FROM usage_events")
    suspend fun deleteAll()
    
    /**
     * Get the count of all events.
     * @return Total number of events
     */
    @Query("SELECT COUNT(*) FROM usage_events")
    suspend fun getCount(): Int
    
    /**
     * Get total usage duration by package name.
     * @param packageName The package name to calculate duration for
     * @return Total duration in milliseconds
     */
    @Query("SELECT SUM(duration_ms) FROM usage_events WHERE package_name = :packageName AND end_time_ms IS NOT NULL")
    suspend fun getTotalDurationByPackage(packageName: String): Long?
}
