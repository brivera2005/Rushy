package com.streamvault.data.manager.recording

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

class RecordingReconcileWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RecordingWorkerEntryPoint {
        fun recordingManager(): com.streamvault.domain.manager.RecordingManager
    }

    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(
            applicationContext,
            RecordingWorkerEntryPoint::class.java
        ).recordingManager()
        return when (manager.reconcileRecordingState()) {
            is com.streamvault.domain.model.Result.Success -> Result.success()
            is com.streamvault.domain.model.Result.Error -> Result.retry()
            com.streamvault.domain.model.Result.Loading -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "RecordingReconcileWorker"
        private const val ONE_SHOT_WORK_NAME = "RecordingReconcileWorkerOneShot"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecordingReconcileWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecordingReconcileWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
