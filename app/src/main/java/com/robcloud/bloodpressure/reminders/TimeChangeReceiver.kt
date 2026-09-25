package com.robcloud.bloodpressure.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Keeps reminders on the phone's current local time. Armed reminder jobs are delays computed in
 * the zone at the time, so when the time zone or the clock changes they are all rebuilt.
 * Both broadcasts are exempt from Android 8's implicit-broadcast limits, so a manifest receiver
 * gets them even while the app isn't running.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return
        ReminderScheduler.realign(context, ReminderStore(context).get())
    }
}
