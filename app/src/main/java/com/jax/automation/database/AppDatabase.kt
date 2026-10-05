package com.jax.automation.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jax.automation.models.AIProviderConfig
import com.jax.automation.models.AutomationLogEntry
import com.jax.automation.models.CharacterLock
import com.jax.automation.models.DownloadRecord
import com.jax.automation.models.LocationLock
import com.jax.automation.models.ObjectLock
import com.jax.automation.models.Project
import com.jax.automation.models.QAResult
import com.jax.automation.models.ReferenceItem
import com.jax.automation.models.Scene
import com.jax.automation.models.StyleLock
import com.jax.automation.models.Task

/**
 * JAX Room database, version 1.
 *
 * Migration strategy: this is the initial schema (v1), so no Migration
 * objects are needed yet. When the schema changes, add explicit Migration
 * objects here — fallbackToDestructiveMigration is deliberately NOT used
 * because the task queue and logs must survive app updates.
 */
@Database(
    entities = [
        Project::class,
        Scene::class,
        Task::class,
        ReferenceItem::class,
        CharacterLock::class,
        StyleLock::class,
        LocationLock::class,
        ObjectLock::class,
        AIProviderConfig::class,
        AutomationLogEntry::class,
        DownloadRecord::class,
        QAResult::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sceneDao(): SceneDao
    abstract fun taskDao(): TaskDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun lockDao(): LockDao
    abstract fun aiProviderDao(): AIProviderDao
    abstract fun logDao(): LogDao
    abstract fun downloadDao(): DownloadDao
    abstract fun qaDao(): QADao
}
