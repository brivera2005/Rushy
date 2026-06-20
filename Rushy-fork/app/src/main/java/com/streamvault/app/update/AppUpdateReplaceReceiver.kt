package com.streamvault.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/**
 * Clears stale OTA state and relaunches Rushy after the package manager finishes an upgrade.
 * The running process still executes old bytecode until it is restarted.
 */
class AppUpdateReplaceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val installer = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    AppUpdateReceiverEntryPoint::class.java,
                ).appUpdateInstaller()
                installer.reconcileInstalledUpdateState()
            } catch (error: Exception) {
                Log.w(TAG, "Failed to reconcile OTA state after package replace", error)
            } finally {
                pendingResult.finish()
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return@launch
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(launchIntent)
            exitProcess(0)
        }
    }

    private companion object {
        private const val TAG = "AppUpdateReplaceReceiver"
    }
}
