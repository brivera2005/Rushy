package com.rushy.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TraktSettings(context: Context) {
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

    var clientIdOverride: String
        get() = prefs.getString(KEY_CLIENT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value.trim()).apply()

    var clientSecretOverride: String
        get() = prefs.getString(KEY_CLIENT_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLIENT_SECRET, value.trim()).apply()

    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        private set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String
        get() = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        private set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var pendingDeviceCode: String
        get() = prefs.getString(KEY_DEVICE_CODE, "") ?: ""
        private set(value) = prefs.edit().putString(KEY_DEVICE_CODE, value).apply()

    var pendingUserCode: String
        get() = prefs.getString(KEY_USER_CODE, "") ?: ""
        private set(value) = prefs.edit().putString(KEY_USER_CODE, value).apply()

    var pendingVerificationUrl: String
        get() = prefs.getString(KEY_VERIFICATION_URL, "") ?: ""
        private set(value) = prefs.edit().putString(KEY_VERIFICATION_URL, value).apply()

    var deviceCodeExpiresAtMs: Long
        get() = prefs.getLong(KEY_DEVICE_EXPIRES, 0L)
        private set(value) = prefs.edit().putLong(KEY_DEVICE_EXPIRES, value).apply()

    var pollIntervalSec: Int
        get() = prefs.getInt(KEY_POLL_INTERVAL, 5)
        private set(value) = prefs.edit().putInt(KEY_POLL_INTERVAL, value).apply()

    fun effectiveClientId(): String =
        clientIdOverride.ifBlank { BuildConfig.TRAKT_CLIENT_ID }.ifBlank { TraktConfig.CLIENT_ID }

    fun effectiveClientSecret(): String =
        clientSecretOverride.ifBlank { BuildConfig.TRAKT_CLIENT_SECRET }

    fun hasClientCredentials(): Boolean =
        effectiveClientId().isNotBlank() && effectiveClientSecret().isNotBlank()

    fun hasClientId(): Boolean = effectiveClientId().isNotBlank()

    fun isConnected(): Boolean = accessToken.isNotBlank()

    fun hasPendingDeviceAuth(): Boolean =
        pendingDeviceCode.isNotBlank() && System.currentTimeMillis() < deviceCodeExpiresAtMs

    fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        clearPendingDeviceAuth()
    }

    fun savePendingDeviceAuth(session: TraktDeviceAuthSession) {
        pendingDeviceCode = session.deviceCode
        pendingUserCode = session.userCode
        pendingVerificationUrl = session.verificationUrl
        deviceCodeExpiresAtMs = session.expiresAtMs
        pollIntervalSec = session.pollIntervalSec
    }

    fun clearPendingDeviceAuth() {
        pendingDeviceCode = ""
        pendingUserCode = ""
        pendingVerificationUrl = ""
        deviceCodeExpiresAtMs = 0L
    }

    fun disconnect() {
        accessToken = ""
        refreshToken = ""
        clearPendingDeviceAuth()
    }

    fun pendingSessionOrNull(): TraktDeviceAuthSession? {
        if (!hasPendingDeviceAuth()) return null
        return TraktDeviceAuthSession(
            userCode = pendingUserCode,
            verificationUrl = pendingVerificationUrl.ifBlank { "https://trakt.tv/activate" },
            deviceCode = pendingDeviceCode,
            expiresAtMs = deviceCodeExpiresAtMs,
            pollIntervalSec = pollIntervalSec,
        )
    }

    companion object {
        private const val PREFS_NAME = "rushy_trakt"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_SECRET = "client_secret"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_USER_CODE = "user_code"
        private const val KEY_VERIFICATION_URL = "verification_url"
        private const val KEY_DEVICE_EXPIRES = "device_expires_at"
        private const val KEY_POLL_INTERVAL = "poll_interval"

        @Volatile
        private var instance: TraktSettings? = null

        fun getInstance(context: Context): TraktSettings {
            return instance ?: synchronized(this) {
                instance ?: TraktSettings(context.applicationContext).also { instance = it }
            }
        }
    }
}
