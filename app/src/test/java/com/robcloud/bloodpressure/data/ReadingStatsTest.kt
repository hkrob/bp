package com.robcloud.bloodpressure.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReadingStatsTest {

    private val sydney = ZoneId.of("Australia/Sydney")

    private fun reading(id: String, at: String, sys: Int = 120, dia: Int = 80, hr: Int = 70) =
        Reading(id, sys, dia, hr, Arm.LEFT, Instant.parse(at))

    @Test
    fun `readings within ten minutes of each other form one sitting`() {
        val readings = listOf(
            reading("a", "2026-07-18T08:00:00Z"),
            reading("b", "2026-07-18T08:04:00Z"),
            reading("c", "2026-07-18T08:14:00Z"), // 10 min after b: still the same sitting
            reading("d", "2026-07-18T08:24:01Z"), // just over 10 min after c: a new sitting
            reading("e", "2026-07-18T20:00:00Z")
        )
        val sittings = groupIntoSittings(readings.shuffled())
        assertEquals(listOf(listOf("e"), listOf("d"), listOf("c", "b", "a")), sittings.map { s -> s.map { it.id } })
    }

    @Test
    fun `a chain of close readings stays one sitting even past the gap overall`() {
        val readings = (0..4).map { reading("r$it", "2026-07-18T08:${"%02d".format(it * 8)}:00Z") }
        assertEquals(1, groupIntoSittings(readings).size)
    }

    @Test
    fun `no readings means no sittings and no average`() {
        assertEquals(emptyList<List<Reading>>(), groupIntoSittings(emptyList()))
        assertNull(averageOf(emptyList()))
    }

    @Test
    fun `averages round to the nearest whole number`() {
        val avg = averageOf(listOf(reading("a", "2026-07-18T08:00:00Z", 121, 80, 70), reading("b", "2026-07-18T08:01:00Z", 122, 81, 71)))!!
        assertEquals(Averages(122, 81, 71, 2), avg) // 121.5 -> 122, 80.5 -> 81, 70.5 -> 71
    }

    @Test
    fun `morning and evening split at local noon`() {
        // Sydney is UTC+10 in July.
        val readings = listOf(
            reading("m1", "2026-07-17T21:30:00Z", sys = 110), // 07:30 local
            reading("m2", "2026-07-18T01:59:00Z", sys = 120), // 11:59 local
            reading("e1", "2026-07-18T02:00:00Z", sys = 140), // 12:00 local
            reading("e2", "2026-07-18T11:00:00Z", sys = 150)  // 21:00 local
        )
        val split = timeOfDayAverages(readings, sydney)
        assertEquals(115, split.morning!!.systolic)
        assertEquals(2, split.morning!!.count)
        assertEquals(145, split.evening!!.systolic)
        assertEquals(2, split.evening!!.count)
    }

    @Test
    fun `a window with no readings is null`() {
        val split = timeOfDayAverages(listOf(reading("m", "2026-07-17T21:30:00Z")), sydney)
        assertEquals(1, split.morning!!.count)
        assertNull(split.evening)
    }
}
