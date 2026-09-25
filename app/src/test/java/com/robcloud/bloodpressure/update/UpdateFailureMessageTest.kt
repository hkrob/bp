package com.robcloud.bloodpressure.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UpdateFailureMessageTest {

    private val now = Instant.parse("2026-09-25T04:00:00Z")

    @Test
    fun `rate limit says so and how long to wait`() {
        val reset = (now.epochSecond + 25 * 60 + 10).toString() // 25 min 10 s away: rounds up
        val msg = updateCheckFailureMessage(403, "0", reset, now)
        assertTrue(msg, msg.contains("hourly limit"))
        assertTrue(msg, msg.contains("about 26 min"))
    }

    @Test
    fun `rate limit with a reset already passed still asks for at least a minute`() {
        val msg = updateCheckFailureMessage(403, "0", (now.epochSecond - 5).toString(), now)
        assertTrue(msg, msg.contains("about 1 min"))
    }

    @Test
    fun `429 counts as the rate limit even without headers`() {
        val msg = updateCheckFailureMessage(429, null, null, now)
        assertTrue(msg, msg.contains("hourly limit"))
        assertTrue(msg, msg.contains("Try again later"))
    }

    @Test
    fun `a 403 with quota left is not called a rate limit`() {
        val msg = updateCheckFailureMessage(403, "12", null, now)
        assertFalse(msg, msg.contains("hourly limit"))
        assertTrue(msg, msg.contains("HTTP 403"))
    }

    @Test
    fun `no release published`() {
        assertTrue(updateCheckFailureMessage(404, "59", null, now).contains("No published release"))
    }
}
