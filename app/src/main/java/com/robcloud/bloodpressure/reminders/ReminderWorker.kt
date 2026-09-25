package com.robcloud.bloodpressure.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminderId = inputData.getString(REMINDER_ID_KEY)
        val settings = ReminderStore(applicationContext).get()
        val time = settings.times.firstOrNull { it.id == reminderId }
        // A job left over from settings that have since changed: don't notify, don't re-arm.
        if (!settings.enabled || time == null) return Result.success()
        try {
            NotificationHelper.showReminder(applicationContext, reminderId)
        } finally {
            ReminderScheduler.scheduleFollowing(applicationContext, time)
        }
        return Result.success()
    }
}
