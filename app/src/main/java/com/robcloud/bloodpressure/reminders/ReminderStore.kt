package com.robcloud.bloodpressure.reminders

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

private const val PREFS_NAME = "reminder_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_TIMES = "times"

data class ReminderTime(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int
)

data class ReminderSettings(val enabled: Boolean, val times: List<ReminderTime>)

class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): ReminderSettings = ReminderSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        times = decodeTimes(prefs.getString(KEY_TIMES, null))
    )

    fun set(settings: ReminderSettings) {
        prefs.edit {
            putBoolean(KEY_ENABLED, settings.enabled)
            putString(KEY_TIMES, encodeTimes(settings.times))
        }
    }

    private fun encodeTimes(times: List<ReminderTime>): String =
        times.joinToString(";") { "${it.id},${it.hour},${it.minute}" }

    private fun decodeTimes(raw: String?): List<ReminderTime> {
        if (raw.isNullOrBlank()) return listOf(ReminderTime(hour = 9, minute = 0))
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size != 3) return@mapNotNull null
            runCatching {
                ReminderTime(id = parts[0], hour = parts[1].toInt(), minute = parts[2].toInt())
            }.getOrNull()
        }
    }
}
