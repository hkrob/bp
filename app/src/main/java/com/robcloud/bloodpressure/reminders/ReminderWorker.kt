package com.robcloud.bloodpressure.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminderId = inputData.getString(REMINDER_ID_KEY)
        NotificationHelper.showReminder(applicationContext, reminderId)
        return Result.success()
    }
}
