package com.robcloud.bloodpressure.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimingTest {

    private val sydney = ZoneId.of("Australia/Sydney")

    private fun at(text: String, zone: ZoneId = sydney) = LocalDateTime.parse(text).atZone(zone)

    @Test
    fun `later today when the time hasn't passed`() {
        assertEquals(at("2026-09-25T09:00"), nextOccurrence(at("2026-09-25T08:30"), 9, 0))
    }

    @Test
    fun `tomorrow when the time has passed or is now`() {
        assertEquals(at("2026-09-26T09:00"), nextOccurrence(at("2026-09-25T09:00"), 9, 0))
        assertEquals(at("2026-09-26T09:00"), nextOccurrence(at("2026-09-25T21:00"), 9, 0))
    }

    @Test
    fun `stays at the same local time across the start of daylight saving`() {
        // Sydney clocks go forward from 02:00 to 03:00 on Sunday 4 October 2026.
        val fired = at("2026-10-03T09:00:05")
        val next = nextOccurrence(fired, 9, 0)
        assertEquals(at("2026-10-04T09:00"), next)
        assertEquals(9, next.hour)
        // A fixed 24 h period would have landed at 10:00 local time.
        assertEquals(Duration.ofHours(23), Duration.between(at("2026-10-03T09:00"), next))
    }

    @Test
    fun `stays at the same local time across the end of daylight saving`() {
        // Sydney clocks go back from 03:00 to 02:00 on Sunday 5 April 2026.
        val next = nextOccurrence(at("2026-04-04T09:00:05"), 9, 0)
        assertEquals(at("2026-04-05T09:00"), next)
        assertEquals(Duration.ofHours(25), Duration.between(at("2026-04-04T09:00"), next))
    }

    @Test
    fun `a time inside the spring-forward gap resolves to just after it`() {
        val next = nextOccurrence(at("2026-10-03T23:00"), 2, 30)
        assertEquals(ZonedDateTime.of(2026, 10, 4, 3, 30, 0, 0, sydney), next)
    }
}
