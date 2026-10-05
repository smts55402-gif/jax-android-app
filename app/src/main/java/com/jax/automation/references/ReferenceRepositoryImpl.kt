package com.jax.automation.references

import com.jax.automation.database.ReferenceDao
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.ReferenceCategory
import com.jax.automation.models.ReferenceItem
import com.jax.automation.models.now
import kotlinx.coroutines.flow.Flow

private const val TAG = "ReferenceRepository"

/** Room-backed implementation of [ReferenceRepository]. */
class ReferenceRepositoryImpl(
    private val dao: ReferenceDao,
    private val logger: JaxLogger
) : ReferenceRepository {

    override suspend fun upsert(item: ReferenceItem): String {
        dao.upsert(item.copy(updatedAt = now()))
        logger.d(TAG, "upsert id=${item.id} category=${item.category} name=${item.name}")
        return item.id
    }

    override suspend fun get(id: String): ReferenceItem? {
        return dao.getById(id)
    }

    override fun observeByCategory(category: ReferenceCategory): Flow<List<ReferenceItem>> {
        return dao.observeByCategory(category)
    }

    override suspend fun findByName(category: ReferenceCategory, name: String): ReferenceItem? {
        return dao.getByCategoryAndName(category, name)
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
        logger.d(TAG, "delete id=$id")
    }
}
