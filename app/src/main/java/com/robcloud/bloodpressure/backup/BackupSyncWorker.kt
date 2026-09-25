package com.robcloud.bloodpressure.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.robcloud.bloodpressure.BloodPressureApp
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val MAX_ATTEMPTS = 5

/**
 * Best-effort background push to the chosen backup folder after a reading is saved, plus a
 * daily run. If the user hasn't picked a folder yet, that's not an error — it just means there's
 * nothing to do until they do so from the History tab. Failures are recorded in
 * [BackupFolderStore] so the app can tell the user their readings aren't being backed up.
 */
class BackupSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BloodPressureApp
        val store = app.backupFolderStore
        return try {
            val result = app.backupSyncManager.sync()
            result.preservedCopy?.let { store.setPendingNotice(unreadableRowsNotice(it)) }
            Result.success()
        } catch (e: NoBackupFolderSelectedException) {
            Result.success()
        } catch (e: BackupFolderAccessLostException) {
            store.recordFailure(e.message.orEmpty())
            Result.failure() // retrying can't help until the user picks the folder again
        } catch (e: ForeignBackupFileException) {
            store.recordFailure(e.message.orEmpty())
            Result.failure()
        } catch (e: CancellationException) {
            throw e // replaced by a newer request (see enqueue) — not a failure
        } catch (e: Exception) {
            store.recordFailure(e.message ?: e.javaClass.simpleName)
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val DAILY_SYNC_WORK_NAME = "daily_auto_sync"
        private const val AFTER_CHANGE_WORK_NAME = "after_change_sync"

        /**
         * Syncs soon after a local change. Unique so rapid saves don't queue a pile of syncs (or
         * of retrying failures): a newer request replaces a pending one, and each run reads the
         * database fresh, so nothing is lost by the replacement.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                AFTER_CHANGE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BackupSyncWorker>().build()
            )
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

fun unreadableRowsNotice(copyName: String): String =
    "Some rows in the backup file couldn't be read, so the original was kept as $copyName"
