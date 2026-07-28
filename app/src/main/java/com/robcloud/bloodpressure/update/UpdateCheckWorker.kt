package com.robcloud.bloodpressure.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.robcloud.bloodpressure.BuildConfig

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val store = UpdatePrefsStore(applicationContext)
            val latest = UpdateManager.checkLatest() ?: return Result.success()
            if (UpdateManager.isNewer(latest.versionName, BuildConfig.VERSION_NAME)) {
                store.saveLatestRelease(latest)
            } else {
                store.clearLatestRelease()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
