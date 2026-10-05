package com.jax.automation.ai

import com.jax.automation.database.AIProviderDao
import com.jax.automation.logging.JaxLogger
import com.jax.automation.models.AIProviderConfig
import com.jax.automation.models.now
import com.jax.automation.security.SecureStorage
import com.jax.automation.settings.SettingsRepository
import kotlinx.coroutines.flow.first

// ---------------------------------------------------------------------------
// Owns provider lifecycle: seeds the 4 built-in configs, builds AIProvider
// instances, joins DAO rows + SecureStorage keys + role defaults, and
// resolves a provider for each ProviderRole. API keys are NEVER logged.
// ---------------------------------------------------------------------------

data class ProviderInfo(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val hasApiKey: Boolean,
    val isDefaultPlanner: Boolean,
    val isDefaultPrompt: Boolean,
    val isDefaultQa: Boolean,
    val defaultModel: String,
    val customBaseUrl: String?
)

class ProviderRegistry(
    private val secureStorage: SecureStorage,
    private val aiProviderDao: AIProviderDao,
    private val settings: SettingsRepository,
    private val logger: JaxLogger
) {

    fun buildProvider(id: String, customBaseUrl: String?): AIProvider = when (id) {
        "gemini" -> GeminiProvider()
        "openai" -> OpenAIProvider()
        "claude" -> ClaudeProvider()
        "other" -> OpenAIProvider(
            baseUrl = customBaseUrl?.ifBlank { null } ?: "https://api.openai.com/v1",
            id = "other",
            displayName = "Other (OpenAI-compatible)"
        )
        else -> throw IllegalArgumentException("Unknown provider id: $id")
    }

    suspend fun ensureSeeded() {
        if (aiProviderDao.getAll().isNotEmpty()) return
        val t = now()
        aiProviderDao.upsert(
            AIProviderConfig(
                id = "gemini", displayName = "Gemini", enabled = true,
                isDefaultPlanner = true, isDefaultPrompt = true, isDefaultQa = true,
                defaultModel = "gemini-2.0-flash", updatedAt = t
            )
        )
        aiProviderDao.upsert(
            AIProviderConfig(
                id = "openai", displayName = "OpenAI", enabled = true,
                defaultModel = "gpt-4o-mini", updatedAt = t
            )
        )
        aiProviderDao.upsert(
            AIProviderConfig(
                id = "claude", displayName = "Claude", enabled = true,
                defaultModel = "claude-3-5-sonnet-latest", updatedAt = t
            )
        )
        aiProviderDao.upsert(
            AIProviderConfig(
                id = "other", displayName = "Other (OpenAI-compatible)", enabled = false,
                defaultModel = "gpt-4o-mini", updatedAt = t
            )
        )
        logger.i("ProviderRegistry", "Seeded 4 default AI provider configs")
    }

    suspend fun providerInfo(): List<ProviderInfo> {
        val s = settings.settings.first()
        return aiProviderDao.getAll().map { row ->
            ProviderInfo(
                id = row.id,
                displayName = row.displayName,
                enabled = row.enabled,
                hasApiKey = secureStorage.contains(SecureStorage.apiKeyFor(row.id)),
                isDefaultPlanner = row.id == s.plannerProviderId,
                isDefaultPrompt = row.id == s.promptProviderId,
                isDefaultQa = row.id == s.qaProviderId,
                defaultModel = row.defaultModel,
                customBaseUrl = row.customBaseUrl
            )
        }
    }

    suspend fun resolve(role: ProviderRole): ResolvedProvider? {
        val s = settings.settings.first()
        val (providerId, model) = when (role) {
            ProviderRole.PLANNER -> s.plannerProviderId to s.plannerModel
            ProviderRole.PROMPT -> s.promptProviderId to s.promptModel
            ProviderRole.QA -> s.qaProviderId to s.qaModel
        }
        val row = aiProviderDao.getById(providerId)
        if (row == null) {
            logger.w("ProviderRegistry", "No provider config found for id $providerId (role $role)")
            return null
        }
        if (!row.enabled) {
            logger.w("ProviderRegistry", "Provider $providerId is disabled (role $role)")
            return null
        }
        val apiKey = secureStorage.get(SecureStorage.apiKeyFor(providerId))
        if (apiKey.isNullOrBlank()) {
            logger.w("ProviderRegistry", "No API key saved for provider $providerId")
            return null
        }
        return ResolvedProvider(buildProvider(providerId, row.customBaseUrl), apiKey, model)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val row = aiProviderDao.getById(id) ?: return
        aiProviderDao.upsert(row.copy(enabled = enabled, updatedAt = now()))
    }

    suspend fun setDefault(role: ProviderRole, id: String) {
        settings.update { current ->
            when (role) {
                ProviderRole.PLANNER -> current.copy(plannerProviderId = id)
                ProviderRole.PROMPT -> current.copy(promptProviderId = id)
                ProviderRole.QA -> current.copy(qaProviderId = id)
            }
        }
        // Clear this role's default flag on all other rows; keep the other
        // roles' flags untouched.
        for (row in aiProviderDao.getAll()) {
            aiProviderDao.upsert(
                row.copy(
                    isDefaultPlanner = if (role == ProviderRole.PLANNER) row.id == id else row.isDefaultPlanner,
                    isDefaultPrompt = if (role == ProviderRole.PROMPT) row.id == id else row.isDefaultPrompt,
                    isDefaultQa = if (role == ProviderRole.QA) row.id == id else row.isDefaultQa,
                    updatedAt = now()
                )
            )
        }
        logger.i("ProviderRegistry", "Default $role provider set to $id")
    }

    suspend fun saveApiKey(id: String, apiKey: String) {
        secureStorage.put(SecureStorage.apiKeyFor(id), apiKey)
    }

    suspend fun deleteApiKey(id: String) {
        secureStorage.remove(SecureStorage.apiKeyFor(id))
    }

    fun hasApiKey(id: String): Boolean =
        secureStorage.contains(SecureStorage.apiKeyFor(id))

    suspend fun saveModel(role: ProviderRole, model: String) {
        settings.update { current ->
            when (role) {
                ProviderRole.PLANNER -> current.copy(plannerModel = model)
                ProviderRole.PROMPT -> current.copy(promptModel = model)
                ProviderRole.QA -> current.copy(qaModel = model)
            }
        }
    }

    suspend fun saveCustomBaseUrl(id: String, baseUrl: String) {
        val row = aiProviderDao.getById(id) ?: return
        aiProviderDao.upsert(row.copy(customBaseUrl = baseUrl, updatedAt = now()))
    }

    suspend fun testConnection(id: String): Boolean = try {
        val row = aiProviderDao.getById(id) ?: return false
        val apiKey = secureStorage.get(SecureStorage.apiKeyFor(id))
        if (apiKey.isNullOrBlank()) return false
        val model = row.defaultModel.ifBlank { settings.settings.first().plannerModel }
        buildProvider(id, row.customBaseUrl).testConnection(apiKey, model)
    } catch (e: Exception) {
        logger.w("ProviderRegistry", "testConnection($id) failed: ${e.message}")
        false
    }
}
