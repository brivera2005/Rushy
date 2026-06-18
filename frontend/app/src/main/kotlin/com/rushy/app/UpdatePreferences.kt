package com.rushy.app

import android.content.Context
import android.content.SharedPreferences

class UpdatePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()

    var checkOnStartup: Boolean
        get() = prefs.getBoolean(KEY_CHECK_ON_STARTUP, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECK_ON_STARTUP, value).apply()

    companion object {
        private const val PREFS_NAME = "rushy_updates"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_CHECK_ON_STARTUP = "check_on_startup"

        @Volatile
        private var instance: UpdatePreferences? = null

        fun getInstance(context: Context): UpdatePreferences {
            return instance ?: synchronized(this) {
                instance ?: UpdatePreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
