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
        NotificationHelper.showReminder(applicationContext, reminderId)
        // Re-read settings rather than reusing the ones from above: the user may have turned
        // reminders off (or edited/removed this one) in the moment it took to show the
        // notification, and re-arming unconditionally in a `finally` would outlive that change.
        val latest = ReminderStore(applicationContext).get()
        val latestTime = latest.times.firstOrNull { it.id == reminderId }
        if (latest.enabled && latestTime != null) {
            ReminderScheduler.scheduleFollowing(applicationContext, latestTime)
        }
        return Result.success()
    }
}
