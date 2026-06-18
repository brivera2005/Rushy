package com.streamvault.data.manager.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.streamvault.data.manager.ProgramReminderManagerImpl
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ProgramReminderAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProgramReminderAlarmReceiverEntryPoint {
        fun reminderManager(): ProgramReminderManagerImpl
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NOTIFY_REMINDER) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        if (reminderId <= 0L) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    appContext,
                    ProgramReminderAlarmReceiverEntryPoint::class.java
                )
                entryPoint.reminderManager().deliverReminder(reminderId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_NOTIFY_REMINDER = "com.streamvault.data.reminder.action.NOTIFY"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
