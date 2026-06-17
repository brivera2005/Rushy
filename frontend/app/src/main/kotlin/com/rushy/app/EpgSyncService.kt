package com.rushy.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EpgSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val force = intent?.getBooleanExtra(EXTRA_FORCE, false) ?: false
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting TV guide sync..."))

        scope.launch {
            try {
                val repo = EpgRepository.getInstance(applicationContext)
                repo.ensureXmltvParsed(force = force)
                val state = repo.loadState.value
                updateNotification(
                    if (state.error != null) {
                        "Guide sync failed: ${state.error}"
                    } else {
                        "EPG: ${state.programCount} programmes, ${state.channelCount} channels"
                    },
                )
            } catch (e: Exception) {
                updateNotification("Guide sync error: ${e.message}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "TV Guide Sync", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rushy TV Guide")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "rushy_epg_sync"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_FORCE = "force"

        fun start(context: Context, force: Boolean = false) {
            val intent = Intent(context, EpgSyncService::class.java).apply {
                putExtra(EXTRA_FORCE, force)
            }
            context.startForegroundService(intent)
        }
    }
}
