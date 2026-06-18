package com.rushy.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
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

    var xtreamPortal: String
        get() = prefs.getString(KEY_XTREAM_PORTAL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_XTREAM_PORTAL, value.trim()).apply()

    var xtreamUsername: String
        get() = prefs.getString(KEY_XTREAM_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_XTREAM_USER, value.trim()).apply()

    var xtreamPassword: String
        get() = prefs.getString(KEY_XTREAM_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_XTREAM_PASS, value).apply()

    var plexServerUrl: String
        get() = prefs.getString(KEY_PLEX_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLEX_URL, value.trim()).apply()

    var plexToken: String
        get() = prefs.getString(KEY_PLEX_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLEX_TOKEN, value.trim()).apply()

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trim()).apply()

    var isDemoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO_MODE, value).apply()

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    fun hasXtreamCredentials(): Boolean =
        xtreamPortal.isNotBlank() && xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()

    fun hasPlexCredentials(): Boolean =
        plexServerUrl.isNotBlank() && plexToken.isNotBlank()

    fun isConfigured(): Boolean = isSetupComplete && (isDemoMode || hasXtreamCredentials())

    fun saveXtream(portal: String, username: String, password: String) {
        xtreamPortal = portal
        xtreamUsername = username
        xtreamPassword = password
    }

    fun savePlex(serverUrl: String, token: String) {
        plexServerUrl = serverUrl
        plexToken = token
    }

    fun enableDemoMode() {
        isDemoMode = true
        isSetupComplete = true
    }

    fun completeSetup() {
        isSetupComplete = true
        isDemoMode = false
    }

    /**
     * Applies [DefaultCredentials] on first launch so test installs skip the wizard.
     */
    fun ensureDevCredentialsIfNeeded() {
        if (!DefaultCredentials.AUTO_APPLY) return
        if (isConfigured()) return
        saveXtream(
            DefaultCredentials.PORTAL_URL,
            DefaultCredentials.USERNAME,
            DefaultCredentials.PASSWORD,
        )
        if (DefaultCredentials.PLEX_SERVER_URL.isNotBlank() &&
            DefaultCredentials.PLEX_TOKEN.isNotBlank()
        ) {
            savePlex(DefaultCredentials.PLEX_SERVER_URL, DefaultCredentials.PLEX_TOKEN)
        }
        if (DefaultCredentials.BACKEND_URL.isNotBlank()) {
            backendUrl = DefaultCredentials.BACKEND_URL
        }
        completeSetup()
    }

    companion object {
        private const val PREFS_NAME = "rushy_credentials"
        private const val KEY_XTREAM_PORTAL = "xtream_portal"
        private const val KEY_XTREAM_USER = "xtream_user"
        private const val KEY_XTREAM_PASS = "xtream_pass"
        private const val KEY_PLEX_URL = "plex_url"
        private const val KEY_PLEX_TOKEN = "plex_token"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_DEMO_MODE = "demo_mode"
        private const val KEY_SETUP_COMPLETE = "setup_complete"

        @Volatile
        private var instance: CredentialStore? = null

        fun getInstance(context: Context): CredentialStore {
            return instance ?: synchronized(this) {
                instance ?: CredentialStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
