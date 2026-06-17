package com.rushy.app

import android.app.ActivityManager
import android.content.Context
import android.util.Log

data class StartupCheckResult(
    val ok: Boolean,
    val checks: List<String>,
    val warnings: List<String> = emptyList(),
) {
    fun log() {
        checks.forEach { Log.i(TAG, "OK: $it") }
        warnings.forEach { Log.w(TAG, "WARN: $it") }
        if (!ok) Log.e(TAG, "Startup validation failed")
    }

    companion object {
        private const val TAG = "StartupValidator"
    }
}

object StartupValidator {
    private const val TAG = "StartupValidator"
    private const val MIN_FREE_RAM_MB = 80L

    suspend fun run(context: Context, credentials: CredentialStore): StartupCheckResult {
        val checks = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val dbOk = LocalMediaRepository.getInstance(context).verifyDatabaseHealth()
        if (dbOk) checks.add("Database accessible") else return fail(checks, "Database health check failed")

        val mem = availableRamMb(context)
        checks.add("Free RAM: ${mem}MB")
        if (mem < MIN_FREE_RAM_MB) {
            warnings.add("Low memory (${mem}MB free) — sync may be slow")
        }

        if (credentials.isDemoMode) {
            checks.add("Demo mode enabled")
            return StartupCheckResult(true, checks, warnings)
        }

        if (credentials.hasXtreamCredentials()) {
            val client = XtreamClient(
                credentials.xtreamPortal,
                credentials.xtreamUsername,
                credentials.xtreamPassword,
            )
            val authOk = client.validateCredentials()
            if (authOk) {
                checks.add("Xtream credentials valid")
            } else {
                return fail(checks, "Xtream credentials rejected by portal")
            }

            val cats = client.fetchLiveCategories()
            checks.add("Live categories: ${cats.size}")
            if (cats.isEmpty()) warnings.add("No live categories returned — portal may be empty")
        } else {
            warnings.add("No Xtream credentials configured")
        }

        return StartupCheckResult(true, checks, warnings)
    }

    fun availableRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    private fun fail(checks: List<String>, reason: String): StartupCheckResult {
        Log.e(TAG, reason)
        return StartupCheckResult(false, checks + "FAIL: $reason")
    }
}
