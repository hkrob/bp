package com.robcloud.bloodpressure.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingOcrTest {
    @Test
    fun `reads the three numbers in order`() {
        val result = parseVisionReply("118 76 82")
        assertEquals(118, result.systolic)
        assertEquals(76, result.diastolic)
        assertEquals(82, result.heartRate)
    }

    @Test
    fun `ignores extra words around the numbers`() {
        val result = parseVisionReply("Sure, the reading is 118 76 82.")
        assertEquals(118, result.systolic)
        assertEquals(76, result.diastolic)
        assertEquals(82, result.heartRate)
    }

    @Test
    fun `a zero for a value the model could not read becomes null, not a guess`() {
        val result = parseVisionReply("118 76 0")
        assertEquals(118, result.systolic)
        assertEquals(76, result.diastolic)
        assertNull(result.heartRate)
    }

    @Test
    fun `corrects a systolic-diastolic pair returned the wrong way round`() {
        val result = parseVisionReply("76 118 82")
        assertEquals(118, result.systolic)
        assertEquals(76, result.diastolic)
        assertEquals(82, result.heartRate)
    }

    @Test
    fun `an out-of-range number is dropped rather than guessed`() {
        val result = parseVisionReply("999 76 82")
        assertNull(result.systolic)
        // The pair-correction only applies when both of a pair are present and in range; 76
        // alone, with nothing to compare it to, is still checked as a plausible diastolic value.
        assertEquals(76, result.diastolic)
        assertEquals(82, result.heartRate)
    }

    @Test
    fun `an empty reply finds nothing`() {
        assertTrue(parseVisionReply("").isEmpty)
    }

    @Test
    fun `a reply with no numbers at all finds nothing`() {
        assertTrue(parseVisionReply("I can't read this display clearly.").isEmpty)
    }

    @Test
    fun `fewer than three numbers fills in what it has`() {
        val result = parseVisionReply("118 76")
        assertEquals(118, result.systolic)
        assertEquals(76, result.diastolic)
        assertNull(result.heartRate)
    }
}
