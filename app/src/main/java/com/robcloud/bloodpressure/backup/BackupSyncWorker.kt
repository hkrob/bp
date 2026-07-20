package com.robcloud.bloodpressure.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.robcloud.bloodpressure.BloodPressureApp
import java.util.concurrent.TimeUnit

/**
 * Best-effort background push to the chosen backup folder after a reading is saved.
 * If the user hasn't picked a folder yet, that's not an error — it just means there's
 * nothing to do until they do so from the History tab.
 */
class BackupSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BloodPressureApp
        return try {
            app.backupSyncManager.sync()
            Result.success()
        } catch (e: NoBackupFolderSelectedException) {
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val DAILY_SYNC_WORK_NAME = "daily_auto_sync"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BackupSyncWorker>().build())
        }

        /**
         * Once-a-day background sync, independent of the after-save sync. Safe to call on
         * every app start — KEEP means an already-scheduled job is left alone rather than
         * restarted, so the daily cadence doesn't reset each launch.
         */
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupSyncWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(DAILY_SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
