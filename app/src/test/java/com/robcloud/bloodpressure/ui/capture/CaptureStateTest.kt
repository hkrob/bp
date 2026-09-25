package com.robcloud.bloodpressure.ui.capture

import com.robcloud.bloodpressure.data.Arm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CaptureStateTest {

    private val now = Instant.parse("2026-09-25T09:00:00Z")

    @Test
    fun `saving clears the entry but keeps the arm and backup status`() {
        val before = CaptureUiState(
            systolic = "135", diastolic = "85", heartRate = "70",
            arm = Arm.RIGHT,
            takenAt = now.minusSeconds(3600), takenAtEdited = true,
            errorMessage = "old", backupBanner = "No backup folder set"
        )
        val after = before.afterSave(crisisBp = null, now = now)
        assertEquals(Arm.RIGHT, after.arm)
        assertEquals("No backup folder set", after.backupBanner)
        assertEquals("", after.systolic + after.diastolic + after.heartRate)
        assertEquals(now, after.takenAt)
        assertFalse(after.takenAtEdited)
        assertNull(after.errorMessage)
        assertTrue(after.justSaved)
    }

    @Test
    fun `saving records the crisis advisory when given`() {
        assertEquals("185/95", CaptureUiState().afterSave(crisisBp = "185/95", now = now).crisisBp)
    }

    @Test
    fun `a picked time is kept, otherwise the moment of saving is used`() {
        val picked = now.minusSeconds(7200)
        assertEquals(picked, CaptureUiState(takenAt = picked, takenAtEdited = true).effectiveTakenAt(now))
        assertEquals(now, CaptureUiState(takenAt = picked, takenAtEdited = false).effectiveTakenAt(now))
    }

    @Test
    fun `stored arm parses, defaulting to left`() {
        assertEquals(Arm.RIGHT, parseStoredArm("RIGHT"))
        assertEquals(Arm.LEFT, parseStoredArm("LEFT"))
        assertEquals(Arm.LEFT, parseStoredArm(null))
        assertEquals(Arm.LEFT, parseStoredArm("garbage"))
    }
}
