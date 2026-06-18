package com.streamvault.app.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.util.Log
import com.streamvault.app.BuildConfig
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RuntimeDiagnosticsManager(
    private val application: Application
) : ComponentCallbacks2 {
    private val activityManager = application.getSystemService(ActivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val outputFile by lazy {
        File(application.filesDir, "diagnostics/runtime-memory.log").also { file ->
            file.parentFile?.mkdirs()
        }
    }
    private var samplingJob: Job? = null
    private var started = false
    private var startedActivityCount = 0

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) {
            writeSnapshot("activity_destroyed:${activity.javaClass.simpleName}")
        }

        override fun onActivityStarted(activity: Activity) {
            startedActivityCount++
            writeSnapshot("activity_started:${activity.javaClass.simpleName}")
            if (startedActivityCount == 1) {
                onAppForegrounded()
            }
        }

        override fun onActivityResumed(activity: Activity) {
            writeSnapshot("activity_resumed:${activity.javaClass.simpleName}")
        }

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            if (startedActivityCount == 0) {
                onAppBackgrounded()
            }
        }
    }

    fun start() {
        if (!BuildConfig.DEBUG || started) return
        started = true
        application.registerComponentCallbacks(this)
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        writeSnapshot("app_start")
    }

    fun stop() {
        if (!started) return
        samplingJob?.cancel()
        samplingJob = null
        application.unregisterActivityLifecycleCallbacks(activityCallbacks)
        application.unregisterComponentCallbacks(this)
        scope.cancel()
        started = false
    }

    private fun onAppForegrounded() {
        writeSnapshot("process_foreground")
        if (samplingJob == null) {
            samplingJob = scope.launch {
                while (isActive) {
                    delay(SAMPLE_INTERVAL_MS)
                    writeSnapshot("periodic")
                }
            }
        }
    }

    private fun onAppBackgrounded() {
        writeSnapshot("process_background")
        samplingJob?.cancel()
        samplingJob = null
    }

    override fun onTrimMemory(level: Int) {
        writeSnapshot("trim_memory:${trimLevelLabel(level)}")
    }

    override fun onLowMemory() {
        writeSnapshot("low_memory")
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private fun writeSnapshot(reason: String) {
        if (!BuildConfig.DEBUG) return
        scope.launch(Dispatchers.IO) {
            val snapshot = captureSnapshot(reason)
            runCatching {
                outputFile.appendText(snapshot + System.lineSeparator())
            }.onFailure { error ->
                Log.w(TAG, "Failed to append runtime diagnostics snapshot: ${error.message}")
            }
            Log.i(TAG, snapshot)
        }
    }

    private fun captureSnapshot(reason: String): String {
        val runtime = Runtime.getRuntime()
        val javaUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        val memoryInfo = ActivityManager.MemoryInfo().also { info ->
            activityManager?.getMemoryInfo(info)
        }
        val debugInfo = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        return buildString {
            append("ts=").append(OffsetDateTime.now().format(formatter))
            append(" reason=").append(reason)
            append(" javaUsedMb=").append(bytesToMb(javaUsedBytes))
            append(" javaMaxMb=").append(bytesToMb(runtime.maxMemory()))
            append(" nativeHeapMb=").append(bytesToMb(Debug.getNativeHeapAllocatedSize()))
            append(" pssKb=").append(debugInfo.totalPss)
            append(" privateDirtyKb=").append(debugInfo.totalPrivateDirty)
            append(" availMemMb=").append(bytesToMb(memoryInfo.availMem))
            append(" lowMemory=").append(memoryInfo.lowMemory)
            append(" trimThresholdMb=").append(bytesToMb(memoryInfo.threshold))
            append(" startedActivities=").append(startedActivityCount)
            append(" memoryClassMb=").append(activityManager?.memoryClass ?: -1)
            append(" largeMemoryClassMb=").append(activityManager?.largeMemoryClass ?: -1)
            append(" sdk=").append(Build.VERSION.SDK_INT)
        }
    }

    private fun bytesToMb(bytes: Long): String {
        return String.format(Locale.US, "%.1f", bytes.toDouble() / (1024.0 * 1024.0))
    }

    private fun trimLevelLabel(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "ui_hidden"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "running_moderate"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "running_low"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "running_critical"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "background"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "moderate"
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "complete"
        else -> level.toString()
    }

    companion object {
        private const val TAG = "RuntimeDiagnostics"
        private const val SAMPLE_INTERVAL_MS = 30_000L
    }
}
