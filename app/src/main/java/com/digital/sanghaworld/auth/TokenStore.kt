package com.digital.sanghaworld.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class TokenStore(context: Context) {
    private val prefs: SharedPreferences = open(context.applicationContext)

    private fun open(app: Context): SharedPreferences {
        try {
            return encrypted(app)
        } catch (_: Exception) {
            app.deleteSharedPreferences(NAME)
        }
        return try {
            encrypted(app)
        } catch (_: Exception) {
            app.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    private fun encrypted(app: Context): SharedPreferences = EncryptedSharedPreferences.create(
        NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        app,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) { prefs.edit().putString(KEY_ACCESS, value).apply() }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) { prefs.edit().putString(KEY_REFRESH, value).apply() }

    var userId: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) { prefs.edit().putString(KEY_USER, value).apply() }

    var displayName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) { prefs.edit().putString(KEY_NAME, value).apply() }

    fun isSignedIn(): Boolean = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun save(access: String, refresh: String, userId: String, displayName: String) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER, userId)
            .putString(KEY_NAME, displayName)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val NAME = "sangha_auth"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_USER = "user"
        private const val KEY_NAME = "name"
    }
}
