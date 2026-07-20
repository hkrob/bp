package com.robcloud.bloodpressure.reminders

/** Bridges the suspend-based notification permission request to an Activity's launcher. */
interface NotificationPermissionHost {
    /** Requests POST_NOTIFICATIONS on Android 13+; returns true immediately on older versions. */
    suspend fun requestNotificationPermission(): Boolean
}
