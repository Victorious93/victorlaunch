package com.victorious.launch.url

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * User-pasted VirusTotal API key, encrypted at rest with an Android Keystore master key.
 * Lives in its own prefs file (excluded from Android backup), never in AppSettings,
 * never in config.export, and is never logged or shown after it is saved.
 */
class VirusTotalKeyStore(context: Context) {
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

    fun hasKey(): Boolean = !key().isNullOrBlank()

    fun key(): String? = prefs?.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun save(value: String): Boolean {
        val p = prefs ?: return false
        return p.edit().putString(KEY, value.trim()).commit()
    }

    fun clear() {
        prefs?.edit()?.remove(KEY)?.commit()
    }

    companion object {
        const val FILE = "url_tools_secure"
        private const val KEY = "vt_api_key"
    }
}
