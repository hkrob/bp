package com.robcloud.bloodpressure.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val WORK_TAG = "daily_reminder"
private const val WORK_NAME_PREFIX = "daily_reminder_"
private const val LEGACY_WORK_NAME = "daily_reminder"
const val REMINDER_ID_KEY = "reminder_id"

/**
 * The next wall-clock occurrence of [hour]:[minute] strictly after [now], in [now]'s zone.
 * Computed from the calendar each time rather than by adding 24 h, so a reminder stays at the
 * same local time across DST changes (the gap between two occurrences is then 23 or 25 h). A
 * time inside a spring-forward gap resolves to just after the gap.
 */
fun nextOccurrence(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    val time = LocalTime.of(hour, minute)
    val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
    if (today.isAfter(now)) return today
    return ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
}

/**
 * Schedules one local notification per reminder time via WorkManager. Not wall-clock exact (no
 * SCHEDULE_EXACT_ALARM permission needed), but close enough for a habit reminder.
 *
 * Each reminder is a one-time job for its next occurrence, which re-arms the following day's when
 * it runs ([ReminderWorker]). A 24 h periodic job would drift later after every delayed run and
 * shift by an hour at each DST change. Job names include the target date, so a job that runs
 * twice (WorkManager is at-least-once) can't create a second chain. Every job shares WORK_TAG so
 * [schedule] can replace the whole set on every save without tracking what existed before.
 */
object ReminderScheduler {
    fun schedule(context: Context, times: List<ReminderTime>) {
        val workManager = WorkManager.getInstance(context)
        cancel(context)
        val now = ZonedDateTime.now()
        times.forEach { enqueueNext(workManager, it, now) }
    }

    /**
     * Arms the next occurrence of each enabled reminder without disturbing ones already armed.
     * Safe to call on every app start; also clears jobs from the old periodic scheme.
     */
    fun ensureScheduled(context: Context, settings: ReminderSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.enabled) {
            cancel(context)
            return
        }
        // The periodic scheme used one unique name per reminder id without a date suffix.
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        settings.times.forEach { workManager.cancelUniqueWork(WORK_NAME_PREFIX + it.id) }
        val now = ZonedDateTime.now()
        settings.times.forEach { enqueueNext(workManager, it, now) }
    }

    /** Called by [ReminderWorker] once it has shown today's notification. */
    fun scheduleFollowing(context: Context, time: ReminderTime, now: ZonedDateTime = ZonedDateTime.now()) {
        enqueueNext(WorkManager.getInstance(context), time, now)
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
        // The single fixed-name job from the old one-reminder-only scheme predates WORK_TAG.
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
    }

    private fun enqueueNext(workManager: WorkManager, time: ReminderTime, now: ZonedDateTime) {
        val target = nextOccurrence(now, time.hour, time.minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, target).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(REMINDER_ID_KEY to time.id))
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            "$WORK_NAME_PREFIX${time.id}_${target.toLocalDate()}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
