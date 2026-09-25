package com.robcloud.bloodpressure.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "update_check"

object UpdateScheduler {
    fun schedule(context: Context, frequency: UpdateCheckFrequency) {
        val workManager = WorkManager.getInstance(context)
        if (frequency == UpdateCheckFrequency.NEVER) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // The check is a GitHub API call: wait for a connection instead of failing and retrying.
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(frequency.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
