package com.robcloud.bloodpressure.ocr

import com.robcloud.bloodpressure.ui.DIASTOLIC_MAX
import com.robcloud.bloodpressure.ui.SYSTOLIC_MAX

/** Scan result. A field is null when nothing plausible was found for it — never a guess. */
data class ReadingOcrResult(val systolic: Int?, val diastolic: Int?, val heartRate: Int?) {
    val isEmpty: Boolean get() = systolic == null && diastolic == null && heartRate == null
}

private val NUMBER = Regex("\\d+")

/**
 * Parses a vision model's reply — asked for three numbers, systolic/diastolic/heart rate in that
 * order, 0 for anything it couldn't read (see [SCAN_PROMPT] in ReadingScanner.kt) — into a
 * result. Never trusts the reply blindly: every value is still checked against the same ranges
 * [com.robcloud.bloodpressure.ui.validateReading] accepts, and a 0 (or a missing number) becomes
 * null, not a guess. Also corrects a systolic/diastolic pair the model got the wrong way round —
 * the higher one is always systolic, which doesn't depend on reading the display correctly.
 */
internal fun parseVisionReply(reply: String): ReadingOcrResult {
    val numbers = NUMBER.findAll(reply).mapNotNull { it.value.toIntOrNull() }.toList()
    val rawSystolic = numbers.getOrNull(0)?.takeIf { it != 0 }
    val rawDiastolic = numbers.getOrNull(1)?.takeIf { it != 0 }
    val rawHeartRate = numbers.getOrNull(2)?.takeIf { it != 0 }

    val (systolic, diastolic) = if (rawSystolic != null && rawDiastolic != null && rawSystolic < rawDiastolic) {
        rawDiastolic to rawSystolic
    } else {
        rawSystolic to rawDiastolic
    }

    return ReadingOcrResult(
        systolic = systolic?.takeIf { it in 60..SYSTOLIC_MAX },
        diastolic = diastolic?.takeIf { it in 40..DIASTOLIC_MAX },
        heartRate = rawHeartRate?.takeIf { it in 30..220 }
    )
}
