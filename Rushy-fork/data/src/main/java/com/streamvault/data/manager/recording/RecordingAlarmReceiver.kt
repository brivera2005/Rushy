package com.streamvault.data.manager.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RecordingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val recordingId = intent.getStringExtra(EXTRA_RECORDING_ID).orEmpty()
        when (intent.action) {
            ACTION_START_RECORDING -> RecordingForegroundService.startCapture(context, recordingId)
            ACTION_STOP_RECORDING -> RecordingForegroundService.stopRecording(context, recordingId)
        }
    }

    companion object {
        const val ACTION_START_RECORDING = "com.streamvault.data.recording.action.START"
        const val ACTION_STOP_RECORDING = "com.streamvault.data.recording.action.STOP"
        const val EXTRA_RECORDING_ID = "recording_id"
    }
}
