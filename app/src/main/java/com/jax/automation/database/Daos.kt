package com.jax.automation.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jax.automation.models.AIProviderConfig
import com.jax.automation.models.AutomationLogEntry
import com.jax.automation.models.CharacterLock
import com.jax.automation.models.DownloadRecord
import com.jax.automation.models.LocationLock
import com.jax.automation.models.ObjectLock
import com.jax.automation.models.Project
import com.jax.automation.models.QAResult
import com.jax.automation.models.ReferenceCategory
import com.jax.automation.models.ReferenceItem
import com.jax.automation.models.Scene
import com.jax.automation.models.StyleLock
import com.jax.automation.models.Task
import com.jax.automation.models.TaskStatus
import com.jax.automation.models.now
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: Project)

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE projectId = :id")
    suspend fun getById(id: String): Project?

    @Query("SELECT * FROM projects WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Project?

    @Delete
    suspend fun delete(project: Project)
}

@Dao
interface SceneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scene: Scene)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(scenes: List<Scene>)

    @Query("SELECT * FROM scenes WHERE projectId = :projectId ORDER BY sceneIndex ASC")
    fun observeByProject(projectId: String): Flow<List<Scene>>

    @Query("SELECT * FROM scenes WHERE projectId = :projectId AND sceneIndex = :index LIMIT 1")
    suspend fun getByProjectAndIndex(projectId: String, index: Int): Scene?

    @Query("DELETE FROM scenes WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: Task)

    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun observeByProject(projectId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    fun observeById(taskId: String): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun getById(taskId: String): Task?

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextPending(projectId: String): Task?

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND sceneId = :sceneId AND type = :type AND status NOT IN ('FAILED','SKIPPED') LIMIT 1")
    suspend fun findActive(projectId: String, sceneId: String, type: String): Task?

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId")
    suspend fun countAll(projectId: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId AND status = 'PENDING'")
    suspend fun countPending(projectId: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId AND status IN ('DOWNLOADED','APPROVED')")
    suspend fun countDone(projectId: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId AND status = 'FAILED'")
    suspend fun countFailed(projectId: String): Int

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun failedTasks(projectId: String): List<Task>

    @Query("UPDATE tasks SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun updateStatus(taskId: String, status: TaskStatus, errorMessage: String?, updatedAt: Long = now())

    @Query("UPDATE tasks SET attemptCount = attemptCount + 1, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun incrementAttempt(taskId: String, updatedAt: Long = now())

    @Query("UPDATE tasks SET resultPath = :resultPath, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun setResult(taskId: String, resultPath: String, updatedAt: Long = now())

    @Query("UPDATE tasks SET status = 'PENDING', errorMessage = NULL, updatedAt = :updatedAt WHERE projectId = :projectId AND status = 'FAILED'")
    suspend fun resetFailed(projectId: String, updatedAt: Long = now()): Int

    @Query("DELETE FROM tasks WHERE projectId = :projectId AND status IN ('DOWNLOADED','APPROVED','SKIPPED')")
    suspend fun clearFinished(projectId: String)
}

@Dao
interface ReferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReferenceItem)

    @Query("SELECT * FROM reference_items WHERE id = :id")
    suspend fun getById(id: String): ReferenceItem?

    @Query("SELECT * FROM reference_items WHERE category = :category ORDER BY name ASC")
    fun observeByCategory(category: ReferenceCategory): Flow<List<ReferenceItem>>

    @Query("SELECT * FROM reference_items WHERE category = :category AND name = :name LIMIT 1")
    suspend fun getByCategoryAndName(category: ReferenceCategory, name: String): ReferenceItem?

    @Query("DELETE FROM reference_items WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface LockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacter(lock: CharacterLock)

    @Query("SELECT * FROM character_locks WHERE characterId = :characterId")
    suspend fun getCharacter(characterId: String): CharacterLock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStyle(lock: StyleLock)

    @Query("SELECT * FROM style_locks WHERE id = 'global' LIMIT 1")
    suspend fun getStyle(): StyleLock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocation(lock: LocationLock)

    @Query("SELECT * FROM location_locks WHERE locationId = :locationId")
    suspend fun getLocation(locationId: String): LocationLock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertObject(lock: ObjectLock)

    @Query("SELECT * FROM object_locks WHERE objectId = :objectId")
    suspend fun getObject(objectId: String): ObjectLock?
}

@Dao
interface AIProviderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: AIProviderConfig)

    @Query("SELECT * FROM ai_providers")
    suspend fun getAll(): List<AIProviderConfig>

    @Query("SELECT * FROM ai_providers")
    fun observeAll(): Flow<List<AIProviderConfig>>

    @Query("SELECT * FROM ai_providers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AIProviderConfig?
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: AutomationLogEntry)

    @Query("SELECT * FROM automation_logs ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<AutomationLogEntry>>

    @Query("DELETE FROM automation_logs WHERE id NOT IN (SELECT id FROM automation_logs ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int = 5000)

    @Query("DELETE FROM automation_logs")
    suspend fun deleteAll()
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord)

    @Query("SELECT * FROM download_records WHERE taskId = :taskId ORDER BY downloadedAt DESC")
    suspend fun getByTask(taskId: String): List<DownloadRecord>

    @Query("SELECT * FROM download_records WHERE projectId = :projectId ORDER BY downloadedAt DESC")
    fun observeByProject(projectId: String): Flow<List<DownloadRecord>>
}

@Dao
interface QADao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: QAResult)

    @Query("SELECT * FROM qa_results WHERE taskId = :taskId ORDER BY checkedAt DESC LIMIT 1")
    suspend fun latestForTask(taskId: String): QAResult?

    @Query("SELECT * FROM qa_results WHERE taskId = :taskId ORDER BY checkedAt DESC")
    fun observeByTask(taskId: String): Flow<List<QAResult>>
}
