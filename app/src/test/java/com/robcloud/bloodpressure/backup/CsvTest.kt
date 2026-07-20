package com.robcloud.bloodpressure.backup

import com.robcloud.bloodpressure.data.Arm
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.data.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CsvTest {

    private fun reading(
        id: String = "r1",
        takenAt: String = "2026-07-18T08:41:00Z",
        sys: Int = 120,
        dia: Int = 80,
        hr: Int = 70,
        arm: Arm = Arm.LEFT
    ) = Reading(id, sys, dia, hr, arm, Instant.parse(takenAt))

    private fun note(
        id: String = "n1",
        date: String = "2026-07-18",
        type: NoteType = NoteType.MEDICATION_CHANGED,
        details: String = "plain details"
    ) = Note(id, LocalDate.parse(date), type, details)

    @Test
    fun `round trip preserves readings and notes`() {
        val readings = listOf(reading(), reading(id = "r2", sys = 135, dia = 85, hr = 90, arm = Arm.RIGHT))
        val notes = listOf(note(), note(id = "n2", type = NoteType.CHECK_UP, details = "annual check"))

        val parsed = Csv.parse(Csv.write(readings, notes))

        assertEquals(readings.toSet(), parsed.readings.toSet())
        assertEquals(notes.toSet(), parsed.notes.toSet())
    }

    @Test
    fun `medication taken notes round trip`() {
        val mt = note(type = NoteType.MEDICATION_TAKEN, details = "Taken at 8:41 am")
        val parsed = Csv.parse(Csv.write(emptyList(), listOf(mt)))
        assertEquals(listOf(mt), parsed.notes)
    }

    @Test
    fun `round trip preserves commas quotes and newlines in details`() {
        val tricky = "line one, with comma\nline \"two\" quoted\r\nline three"
        val parsed = Csv.parse(Csv.write(emptyList(), listOf(note(details = tricky))))

        assertEquals(tricky, parsed.notes.single().details)
    }

    @Test
    fun `formula injection is neutralised in written file but round trips losslessly`() {
        for (payload in listOf("=HYPERLINK(\"x\")", "+1+2", "-1", "@SUM(A1)", "'quoted start")) {
            val written = Csv.write(emptyList(), listOf(note(details = payload)))
            val detailsCell = written.lineSequence().first { it.startsWith("NOTE,") || it.startsWith("\"NOTE") }
            assertFalse(
                "cell must not start raw with a formula trigger: $detailsCell",
                detailsCell.split(",").last().firstOrNull() in listOf('=', '+', '-', '@')
            )
            assertEquals(payload, Csv.parse(written).notes.single().details)
        }
    }

    @Test
    fun `legacy reading-only header still parses`() {
        val legacy = """
            id,taken_at,systolic_mmhg,diastolic_mmhg,heart_rate_bpm,arm
            abc,2026-07-18T08:41:00Z,120,80,70,LEFT
        """.trimIndent()

        val parsed = Csv.parse(legacy)

        assertEquals(1, parsed.readings.size)
        assertEquals("abc", parsed.readings.single().id)
        assertTrue(parsed.notes.isEmpty())
    }

    @Test
    fun `corrupt rows are skipped instead of failing the whole import`() {
        val good = reading()
        val written = Csv.write(listOf(good), emptyList()) +
            "READING,bad,not-a-date,x,y,z,NOPE,,\n" +
            "GARBAGE\n"

        val parsed = Csv.parse(written)

        assertEquals(listOf(good), parsed.readings)
    }

    @Test
    fun `empty input parses to empty result`() {
        val parsed = Csv.parse("")
        assertTrue(parsed.readings.isEmpty() && parsed.notes.isEmpty())
    }

    @Test
    fun `unknown record types are ignored`() {
        val written = Csv.write(listOf(reading()), emptyList()) +
            "FUTURE_TYPE,x,2026-01-01,,,,,,some data\n"
        val parsed = Csv.parse(written)
        assertEquals(1, parsed.readings.size)
        assertTrue(parsed.notes.isEmpty())
    }
}
