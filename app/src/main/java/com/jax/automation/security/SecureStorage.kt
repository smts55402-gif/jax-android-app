package com.jax.automation.security

/**
 * Secure key/value storage contract. The implementation uses
 * EncryptedSharedPreferences. Used for AI provider API keys and any other
 * secret material — secrets must never live in Room, DataStore, logs,
 * or plain SharedPreferences.
 */
interface SecureStorage {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
    fun contains(key: String): Boolean

    companion object {
        fun apiKeyFor(providerId: String): String = "api_key_$providerId"
    }
}
