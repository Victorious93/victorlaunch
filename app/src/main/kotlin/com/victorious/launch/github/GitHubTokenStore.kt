package com.victorious.launch.github

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Fine-grained GitHub token, encrypted at rest with an Android Keystore master key.
 * Lives in its own prefs file (excluded from Android backup), never in AppSettings,
 * never in config.export, and is never logged or shown after it is saved.
 */
class GitHubTokenStore(context: Context) {
    private val app = context.applicationContext

    private val prefs: SharedPreferences? by lazy {
        try {
            val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                FILE,
                key,
                app,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun available(): Boolean = prefs != null

    fun hasToken(): Boolean = !token().isNullOrBlank()

    fun token(): String? = prefs?.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun save(token: String): Boolean {
        val p = prefs ?: return false
        return p.edit().putString(KEY, token.trim()).commit()
    }

    fun clear() {
        prefs?.edit()?.remove(KEY)?.commit()
    }

    companion object {
        const val FILE = "github_secure"
        private const val KEY = "gh_fine_grained"
    }
}
