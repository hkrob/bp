package com.robcloud.bloodpressure.ui.capture

import android.content.Context
import androidx.core.content.edit
import com.robcloud.bloodpressure.data.Arm

private const val PREFS_NAME = "capture_prefs"
private const val KEY_LAST_ARM = "last_arm"

/** Stored arm name to [Arm]; LEFT when nothing (or something unrecognised) is stored. */
fun parseStoredArm(name: String?): Arm = Arm.entries.firstOrNull { it.name == name } ?: Arm.LEFT

/**
 * Remembers the arm last used on Add reading. Most people always measure the same arm, and
 * resetting to Left after every save quietly recorded right-arm readings as left-arm ones.
 */
class CapturePrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastArm(): Arm = parseStoredArm(prefs.getString(KEY_LAST_ARM, null))

    fun setLastArm(arm: Arm) {
        prefs.edit { putString(KEY_LAST_ARM, arm.name) }
    }
}
