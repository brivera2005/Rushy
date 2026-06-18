package com.streamvault.data.manager.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.streamvault.domain.model.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager: AlarmManager? by lazy {
        context.getSystemService(AlarmManager::class.java)
    }

    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    fun scheduleStart(recordingId: String, whenMs: Long): Result<Unit> {
        return schedule(
            action = RecordingAlarmReceiver.ACTION_START_RECORDING,
            recordingId = recordingId,
            whenMs = whenMs
        )
    }

    fun scheduleStop(recordingId: String, whenMs: Long): Result<Unit> {
        return schedule(
            action = RecordingAlarmReceiver.ACTION_STOP_RECORDING,
            recordingId = recordingId,
            whenMs = whenMs
        )
    }

    fun cancel(recordingId: String) {
        alarmManager?.cancel(
            buildPendingIntent(
                action = RecordingAlarmReceiver.ACTION_START_RECORDING,
                recordingId = recordingId,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        alarmManager?.cancel(
            buildPendingIntent(
                action = RecordingAlarmReceiver.ACTION_STOP_RECORDING,
                recordingId = recordingId,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun schedule(action: String, recordingId: String, whenMs: Long): Result<Unit> {
        val pendingIntent = buildPendingIntent(
            action = action,
            recordingId = recordingId,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = alarmManager ?: return Result.error(EXACT_ALARM_PERMISSION_MESSAGE)
        if (!canScheduleExactAlarms()) {
            return Result.error(EXACT_ALARM_PERMISSION_MESSAGE)
        }
        return runCatching {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                manager,
                AlarmManager.RTC_WAKEUP,
                whenMs,
                pendingIntent
            )
            Result.success(Unit)
        }.getOrElse { ex ->
            android.util.Log.w(
                "RecordingAlarmScheduler",
                "Exact alarm scheduling failed for $recordingId",
                ex
            )
            Result.error(EXACT_ALARM_PERMISSION_MESSAGE, ex)
        }
    }

    private fun buildPendingIntent(action: String, recordingId: String, flags: Int): PendingIntent {
        val intent = Intent(context, RecordingAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(RecordingAlarmReceiver.EXTRA_RECORDING_ID, recordingId)
        return PendingIntent.getBroadcast(
            context,
            requestCode(action, recordingId),
            intent,
            flags
        )
    }

    private fun requestCode(action: String, recordingId: String): Int {
        val uuid = runCatching { java.util.UUID.fromString(recordingId) }.getOrNull()
        val idHash = if (uuid != null) {
            (uuid.mostSignificantBits xor uuid.leastSignificantBits).toInt()
        } else {
            recordingId.hashCode()
        }
        return 31 * action.hashCode() + idHash
    }

    companion object {
        const val EXACT_ALARM_PERMISSION_MESSAGE = "Exact alarm access is required for reliable DVR scheduling. Enable exact alarms for StreamVault in system settings and try again."
    }
}
