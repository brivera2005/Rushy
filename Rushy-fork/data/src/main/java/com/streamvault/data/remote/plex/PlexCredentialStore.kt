package com.streamvault.data.remote.plex

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var backupEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKUP_ENABLED, value).apply()

    fun hasCredentials(): Boolean = serverUrl.isNotBlank() && token.isNotBlank()

    fun save(serverUrl: String, token: String) {
        this.serverUrl = serverUrl
        this.token = token
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_TOKEN)
            .putBoolean(KEY_BACKUP_ENABLED, false)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "rushy_plex_credentials"
        const val KEY_SERVER_URL = "plex_server_url"
        const val KEY_TOKEN = "plex_token"
        const val KEY_BACKUP_ENABLED = "plex_backup_enabled"
    }
}
