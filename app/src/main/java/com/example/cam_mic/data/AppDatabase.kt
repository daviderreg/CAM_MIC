package com.example.cam_mic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room Database for storing microphone and camera usage events.
 * Uses singleton pattern with thread-safe double-checked locking.
 */
@Database(
    entities = [UsageEventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * Get the DAO for usage events.
     */
    abstract fun usageEventDao(): UsageEventDao
    
    companion object {
        private const val DATABASE_NAME = "cam_mic_usage_db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Get the singleton database instance.
         * @param context Application context
         * @return AppDatabase instance
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Callback for database creation and open events.
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-populate or initialize data if needed
            }
        }
    }
}

/**
 * Extension function to get the repository from context.
 */
fun Context.getUsageEventRepository(): UsageEventRepository {
    return UsageEventRepository(AppDatabase.getDatabase(this).usageEventDao())
}
