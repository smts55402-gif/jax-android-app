package com.jax.automation.references

import com.jax.automation.database.LockDao
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.CharacterLock
import com.jax.automation.models.LocationLock
import com.jax.automation.models.ObjectLock
import com.jax.automation.models.StyleLock

private const val TAG = "LockManager"

/** Room-backed implementation of [LockManager]. */
class LockManagerImpl(
    private val lockDao: LockDao,
    private val logger: JaxLogger
) : LockManager {

    override suspend fun getCharacterLock(characterId: String): CharacterLock? {
        return lockDao.getCharacter(characterId)
    }

    override suspend fun saveCharacterLock(lock: CharacterLock) {
        lockDao.upsertCharacter(lock)
        logger.d(TAG, "saveCharacterLock characterId=${lock.characterId}")
    }

    override suspend fun getStyleLock(): StyleLock? {
        return lockDao.getStyle()
    }

    override suspend fun saveStyleLock(lock: StyleLock) {
        val enforced = lock.copy(id = "global")
        lockDao.upsertStyle(enforced)
        logger.d(TAG, "saveStyleLock id=global")
    }

    override suspend fun getLocationLock(locationId: String): LocationLock? {
        return lockDao.getLocation(locationId)
    }

    override suspend fun saveLocationLock(lock: LocationLock) {
        lockDao.upsertLocation(lock)
        logger.d(TAG, "saveLocationLock locationId=${lock.locationId}")
    }

    override suspend fun getObjectLock(objectId: String): ObjectLock? {
        return lockDao.getObject(objectId)
    }

    override suspend fun saveObjectLock(lock: ObjectLock) {
        lockDao.upsertObject(lock)
        logger.d(TAG, "saveObjectLock objectId=${lock.objectId}")
    }
}
