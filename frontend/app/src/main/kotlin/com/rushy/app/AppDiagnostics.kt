package com.rushy.app

import android.content.Context
import java.io.File

object AppDiagnostics {
    private const val PREFS = "rushy_diagnostics"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_ERROR_TIME = "last_error_time"

    fun recordError(context: Context, error: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ERROR, error)
            .putLong(KEY_LAST_ERROR_TIME, System.currentTimeMillis())
            .apply()
    }

    fun lastError(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ERROR, null)
            ?.takeIf { it.isNotBlank() }

    fun lastErrorTime(context: Context): Long =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_ERROR_TIME, 0L)

    fun clearError(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_ERROR)
            .remove(KEY_LAST_ERROR_TIME)
            .apply()
    }

    fun readCrashLogTail(context: Context, maxLines: Int = 40): String? {
        val file = File(context.filesDir, "rushy_crash.log")
        if (!file.exists()) return null
        return runCatching {
            file.readLines().takeLast(maxLines).joinToString("\n")
        }.getOrNull()
    }
}
