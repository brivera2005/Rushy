package com.streamvault.data.manager.recording

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RecordingServiceLauncher @Inject constructor() {
    open fun startCapture(context: Context, recordingId: String) {
        RecordingForegroundService.startCapture(context, recordingId)
    }

    open fun stopRecording(context: Context, recordingId: String) {
        RecordingForegroundService.stopRecording(context, recordingId)
    }

    open fun requestReconcile(context: Context) {
        RecordingForegroundService.requestReconcile(context)
    }

    open fun stopIfIdle(context: Context) {
        RecordingForegroundService.stopIfIdle(context)
    }
}