package com.robcloud.bloodpressure.data

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Readings taken this close together (each to the one before) count as one sitting. */
val SITTING_GAP: Duration = Duration.ofMinutes(10)

/** Readings before this local time count as morning; from it on, as evening. */
val EVENING_STARTS: LocalTime = LocalTime.NOON

data class Averages(val systolic: Int, val diastolic: Int, val heartRate: Int, val count: Int) {
    val category: BpCategory get() = BpCategory.of(systolic, diastolic)
}

fun averageOf(readings: List<Reading>): Averages? {
    if (readings.isEmpty()) return null
    return Averages(
        systolic = readings.map { it.systolicMmHg }.average().roundToInt(),
        diastolic = readings.map { it.diastolicMmHg }.average().roundToInt(),
        heartRate = readings.map { it.heartRateBpm }.average().roundToInt(),
        count = readings.size
    )
}

/**
 * Groups readings into sittings: a run of readings each within [gap] of the one before, as when
 * a monitor is used two or three times in a row. Sittings come newest first, and the readings in
 * each keep newest-first order, matching the History list.
 */
fun groupIntoSittings(readings: List<Reading>, gap: Duration = SITTING_GAP): List<List<Reading>> {
    val sittings = mutableListOf<MutableList<Reading>>()
    for (reading in readings.sortedByDescending { it.takenAt }) {
        val current = sittings.lastOrNull()
        if (current != null && Duration.between(reading.takenAt, current.last().takenAt) <= gap) {
            current.add(reading)
        } else {
            sittings.add(mutableListOf(reading))
        }
    }
    return sittings
}

data class TimeOfDayAverages(val morning: Averages?, val evening: Averages?)

/** Averages split by local clock time: before [EVENING_STARTS], and from it on. */
fun timeOfDayAverages(readings: List<Reading>, zone: ZoneId): TimeOfDayAverages {
    val (morning, evening) = readings.partition {
        it.takenAt.atZone(zone).toLocalTime().isBefore(EVENING_STARTS)
    }
    return TimeOfDayAverages(averageOf(morning), averageOf(evening))
}
