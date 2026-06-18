package com.streamvault.data.manager.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.streamvault.data.local.entity.ProgramReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgramReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun showReminder(reminder: ProgramReminderEntity) {
        createChannelIfNeeded()
        val now = System.currentTimeMillis()
        val minutesUntilStart = ((reminder.programStartTime - now) / 60000L).coerceAtLeast(0L)
        val contentText = if (minutesUntilStart <= 0L) {
            "${reminder.channelName} is starting now."
        } else {
            "${reminder.channelName} starts in ${minutesUntilStart} min."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Starting soon: ${reminder.programTitle}")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(buildLaunchPendingIntent())
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(reminderNotificationTag(reminder.id), 0, notification)
        }
    }

    private fun reminderNotificationTag(reminderId: Long): String = "program-reminder:$reminderId"

    private fun buildLaunchPendingIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Program reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts before scheduled live programs start"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "program-reminders"
    }
}
