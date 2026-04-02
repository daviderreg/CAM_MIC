package com.example.cam_mic.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository for usage events.
 * Provides a clean API for data operations, abstracting the database layer.
 * 
 * @param dao The DAO to use for database operations
 */
class UsageEventRepository(private val dao: UsageEventDao) {
    
    /**
     * Get all usage events as a Flow.
     * @return Flow of all events ordered by start time (newest first)
     */
    fun getAllEvents(): Flow<List<UsageEventEntity>> {
        return dao.getAllEvents()
    }
    
    /**
     * Get all usage events as a one-time list.
     * @return List of all events ordered by start time (newest first)
     */
    suspend fun getAllEventsOnce(): List<UsageEventEntity> {
        return dao.getAllEventsOnce()
    }
    
    /**
     * Get ongoing events (events with null end_time_ms).
     * @return List of ongoing events
     */
    suspend fun getOngoingEvents(): List<UsageEventEntity> {
        return dao.getOngoingEvents()
    }
    
    /**
     * Get events by package name.
     * @param packageName The package name to filter by
     * @return Flow of events for the specified package
     */
    fun getEventsByPackage(packageName: String): Flow<List<UsageEventEntity>> {
        return dao.getEventsByPackage(packageName)
    }
    
    /**
     * Get events by resource type.
     * @param resourceType The resource type to filter by
     * @return Flow of events for the specified resource type
     */
    fun getEventsByResourceType(resourceType: ResourceType): Flow<List<UsageEventEntity>> {
        return dao.getEventsByResourceType(resourceType)
    }
    
    /**
     * Get events within a date range.
     * @param startTimeMs Start timestamp in milliseconds
     * @param endTimeMs End timestamp in milliseconds
     * @return Flow of events within the date range
     */
    fun getEventsByDateRange(startTimeMs: Long, endTimeMs: Long): Flow<List<UsageEventEntity>> {
        return dao.getEventsByDateRange(startTimeMs, endTimeMs)
    }
    
    /**
     * Insert a new usage event.
     * @param event The event to insert
     * @return The row ID of the inserted event
     */
    suspend fun insert(event: UsageEventEntity): Long {
        return dao.insert(event)
    }
    
    /**
     * Update an existing usage event.
     * @param event The event to update
     */
    suspend fun update(event: UsageEventEntity) {
        dao.update(event)
    }
    
    /**
     * Delete a usage event.
     * @param event The event to delete
     */
    suspend fun delete(event: UsageEventEntity) {
        dao.delete(event)
    }
    
    /**
     * Delete all usage events.
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }
    
    /**
     * Get the count of all events.
     * @return Total number of events
     */
    suspend fun getCount(): Int {
        return dao.getCount()
    }
    
    /**
     * Get total usage duration by package name.
     * @param packageName The package name to calculate duration for
     * @return Total duration in milliseconds
     */
    suspend fun getTotalDurationByPackage(packageName: String): Long? {
        return dao.getTotalDurationByPackage(packageName)
    }
}
