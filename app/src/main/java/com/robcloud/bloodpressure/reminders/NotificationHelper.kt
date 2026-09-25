package com.robcloud.bloodpressure.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.robcloud.bloodpressure.MainActivity
import com.robcloud.bloodpressure.R

private const val CHANNEL_ID = "daily_reminder"
private const val DEFAULT_NOTIFICATION_ID = 1001
private const val REMINDER_REQUEST_CODE = 1

/**
 * The same intent the launcher uses, so tapping a notification or the widget brings an already
 * running app to the front as it was, instead of stacking or recreating the screen (which would
 * drop a half-entered reading).
 */
fun appLaunchIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

object NotificationHelper {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminds you to log a blood pressure reading"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Whether a reminder posted now would actually be shown: the runtime permission (Android 13+),
     * the app-level switch, and the reminder channel must all allow it.
     */
    fun canShowReminders(context: Context): Boolean {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun showReminder(context: Context, reminderId: String? = null) {
        ensureChannel(context)
        if (!canShowReminders(context)) return

        val pendingIntent = PendingIntent.getActivity(
            context, REMINDER_REQUEST_CODE, appLaunchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time for a blood pressure reading")
            .setContentText("Tap to log today's reading in BP Tracker")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = reminderId?.hashCode() ?: DEFAULT_NOTIFICATION_ID
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post — nothing to show.
        }
    }
}
