package com.jax.automation.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * [SecureStorage] backed by [EncryptedSharedPreferences] (AES-256 key
 * encryption + AES-256-GCM value encryption, Tink keyset in the Android
 * Keystore via [MasterKey]).
 *
 * Used for AI provider API keys and any other secret material — secrets must
 * never live in Room, DataStore, logs, or plain SharedPreferences.
 *
 * Strict by design: if encrypted storage cannot be initialised, construction
 * throws [IllegalStateException]. There is deliberately NO plaintext fallback,
 * so secrets can never silently land in unencrypted storage.
 */
class SecureStorageImpl(context: Context) : SecureStorage {

    private val prefs: SharedPreferences = try {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "jax_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        throw IllegalStateException("Secure storage unavailable: ${e.message}", e)
    }

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)
}
