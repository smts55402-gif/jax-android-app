package com.jax.automation.references

import com.jax.automation.models.CharacterLock
import com.jax.automation.models.LocationLock
import com.jax.automation.models.ObjectLock
import com.jax.automation.models.ReferenceCategory
import com.jax.automation.models.ReferenceItem
import com.jax.automation.models.StyleLock
import kotlinx.coroutines.flow.Flow

/** CRUD contract for the Reference Library. */
interface ReferenceRepository {
    suspend fun upsert(item: ReferenceItem): String
    suspend fun get(id: String): ReferenceItem?
    fun observeByCategory(category: ReferenceCategory): Flow<List<ReferenceItem>>
    suspend fun findByName(category: ReferenceCategory, name: String): ReferenceItem?
    suspend fun delete(id: String)
}

/**
 * Consistency-lock contract. Locks are enforced by PromptBuilder: locked
 * attributes can never be dropped or randomly redesigned when a scene
 * prompt is assembled.
 */
interface LockManager {
    suspend fun getCharacterLock(characterId: String): CharacterLock?
    suspend fun saveCharacterLock(lock: CharacterLock)
    suspend fun getStyleLock(): StyleLock?
    suspend fun saveStyleLock(lock: StyleLock)
    suspend fun getLocationLock(locationId: String): LocationLock?
    suspend fun saveLocationLock(lock: LocationLock)
    suspend fun getObjectLock(objectId: String): ObjectLock?
    suspend fun saveObjectLock(lock: ObjectLock)
}
