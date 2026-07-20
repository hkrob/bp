package com.robcloud.bloodpressure.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val WORK_TAG = "daily_reminder"
private const val WORK_NAME_PREFIX = "daily_reminder_"
private const val LEGACY_WORK_NAME = "daily_reminder"
const val REMINDER_ID_KEY = "reminder_id"

/**
 * Schedules one daily local notification per reminder time via WorkManager. Not wall-clock
 * exact (no SCHEDULE_EXACT_ALARM permission needed), but close enough for a habit reminder —
 * each reminder's first run is timed to its requested hour/minute, then repeats every 24h.
 * Every scheduled request shares WORK_TAG so `schedule` can cleanly replace the whole set
 * on every save without having to track which ids existed before.
 */
object ReminderScheduler {
    fun schedule(context: Context, times: List<ReminderTime>) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
        // Cleans up the single fixed-name job from the old one-reminder-only scheme, which
        // predates WORK_TAG and would otherwise keep firing forever alongside the new ones.
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)

        val now = LocalDateTime.now()
        times.forEach { time ->
            var target = now.toLocalDate().atTime(LocalTime.of(time.hour, time.minute))
            if (!target.isAfter(now)) {
                target = target.plusDays(1)
            }
            val initialDelay = Duration.between(now, target).toMillis()

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(REMINDER_ID_KEY to time.id))
                .addTag(WORK_TAG)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PREFIX + time.id,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
    }
}
