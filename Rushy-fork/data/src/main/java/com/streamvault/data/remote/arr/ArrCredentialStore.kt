package com.streamvault.data.remote.arr

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArrCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = try {
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

    var radarrUrl: String
        get() = prefs.getString(KEY_RADARR_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RADARR_URL, value.trim()).apply()

    var radarrApiKey: String
        get() = prefs.getString(KEY_RADARR_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RADARR_API_KEY, value.trim()).apply()

    var sonarrUrl: String
        get() = prefs.getString(KEY_SONARR_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SONARR_URL, value.trim()).apply()

    var sonarrApiKey: String
        get() = prefs.getString(KEY_SONARR_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SONARR_API_KEY, value.trim()).apply()

    fun hasRadarr(): Boolean = radarrUrl.isNotBlank() && radarrApiKey.isNotBlank()

    fun hasSonarr(): Boolean = sonarrUrl.isNotBlank() && sonarrApiKey.isNotBlank()

    fun hasAnyArr(): Boolean = hasRadarr() || hasSonarr()

    fun saveRadarr(url: String, apiKey: String) {
        radarrUrl = url
        radarrApiKey = apiKey
    }

    fun saveSonarr(url: String, apiKey: String) {
        sonarrUrl = url
        sonarrApiKey = apiKey
    }

    private companion object {
        const val PREFS_NAME = "rushy_arr_credentials"
        const val KEY_RADARR_URL = "radarr_url"
        const val KEY_RADARR_API_KEY = "radarr_api_key"
        const val KEY_SONARR_URL = "sonarr_url"
        const val KEY_SONARR_API_KEY = "sonarr_api_key"
    }
}
