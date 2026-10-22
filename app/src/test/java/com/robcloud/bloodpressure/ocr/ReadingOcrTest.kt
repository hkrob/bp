package com.robcloud.bloodpressure.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reference layout: this is what ML Kit's elements look like for the Omron photo this was built against. */
private val OMRON_SAMPLE = listOf(
    OcrToken("HEM-7200", top = 20, height = 40),
    OcrToken("118", top = 150, height = 200),
    OcrToken("76", top = 400, height = 200),
    OcrToken("135", top = 430, height = 35), // printed scale marker, not the reading
    OcrToken("85", top = 700, height = 35),  // printed scale marker, not the reading
    OcrToken("82", top = 750, height = 170)
)

class ReadingOcrTest {
    @Test
    fun `reads the big digits top to bottom, ignoring smaller printed text nearby`() {
        val result = parseReadingTokens(OMRON_SAMPLE)
        assertEquals(118, result.systolic)
        assertEquals(76, result.diastolic)
        assertEquals(82, result.heartRate)
    }

    @Test
    fun `corrects a layout with diastolic printed above systolic`() {
        val tokens = listOf(
            OcrToken("76", top = 100, height = 200),
            OcrToken("118", top = 350, height = 200),
            OcrToken("82", top = 600, height = 200)
        )
        val result = parseReadingTokens(tokens)
        assertEquals(118, result.systolic)
        assertEquals(76, result.diastolic)
        assertEquals(82, result.heartRate)
    }

    @Test
    fun `fills in only what it found, leaving the rest null`() {
        val result = parseReadingTokens(listOf(OcrToken("118", top = 0, height = 200)))
        assertEquals(118, result.systolic)
        assertNull(result.diastolic)
        assertNull(result.heartRate)
        assertTrue(!result.isEmpty)
    }

    @Test
    fun `finds nothing in unrelated text`() {
        val result = parseReadingTokens(listOf(OcrToken("HEM-7200", top = 0, height = 40)))
        assertTrue(result.isEmpty)
    }

    @Test
    fun `empty input finds nothing`() {
        assertTrue(parseReadingTokens(emptyList()).isEmpty)
    }

    @Test
    fun `an out-of-range big number for a field is dropped rather than guessed`() {
        // A 4-digit stray number this large would already be filtered by the 10..300 net; this
        // checks a 3-digit number that's simply outside the physiological range for any field.
        val tokens = listOf(
            OcrToken("301", top = 0, height = 200), // just over the net below, excluded outright
            OcrToken("30", top = 300, height = 200) // in-range for nothing in particular at this position
        )
        val result = parseReadingTokens(tokens)
        // "301" never becomes a candidate at all (filtered by the broad 10..300 net before
        // field-specific ranges apply), so the remaining single candidate "30" is tried as
        // systolic and fails the 60..260 range, leaving every field null.
        assertTrue(result.isEmpty)
    }

    @Test
    fun `a single pair with systolic lower than diastolic still assigns the higher one as systolic`() {
        val tokens = listOf(
            OcrToken("65", top = 0, height = 200),
            OcrToken("110", top = 300, height = 200)
        )
        val result = parseReadingTokens(tokens)
        assertEquals(110, result.systolic)
        assertEquals(65, result.diastolic)
    }

    @Test
    fun `a multi-number token is split into separate candidates`() {
        val tokens = listOf(OcrToken("118/76", top = 100, height = 200), OcrToken("82", top = 400, height = 200))
        val result = parseReadingTokens(tokens)
        assertEquals(118, result.systolic)
        assertEquals(76, result.diastolic)
        assertEquals(82, result.heartRate)
    }
}
