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
import java.time.LocalTime

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
        details: String = "plain details",
        time: LocalTime = LocalTime.of(0, 1)
    ) = Note(id, LocalDate.parse(date), type, details, time)

    @Test
    fun `round trip preserves readings and notes`() {
        val readings = listOf(reading(), reading(id = "r2", sys = 135, dia = 85, hr = 90, arm = Arm.RIGHT))
        val notes = listOf(note(), note(id = "n2", type = NoteType.CHECK_UP, details = "annual check"))

        val parsed = Csv.parse(Csv.write(readings, notes))

        assertEquals(readings.toSet(), parsed.readings.toSet())
        assertEquals(notes.toSet(), parsed.notes.toSet())
    }

    @Test
    fun `medication taken note preserves its time`() {
        val mt = note(id = "mt", type = NoteType.MEDICATION_TAKEN, details = "", time = LocalTime.of(16, 24))
        val parsed = Csv.parse(Csv.write(emptyList(), listOf(mt)))
        assertEquals(mt, parsed.notes.single())
        assertEquals(LocalTime.of(16, 24), parsed.notes.single().time)
    }

    @Test
    fun `note time round trips for all types`() {
        val notes = listOf(
            note(id = "a", type = NoteType.OTHER, details = "morning", time = LocalTime.of(7, 5)),
            note(id = "b", type = NoteType.MEDICATION_TAKEN, details = "", time = LocalTime.of(22, 30))
        )
        assertEquals(notes.toSet(), Csv.parse(Csv.write(emptyList(), notes)).notes.toSet())
    }

    @Test
    fun `legacy date-only note imports at one minute past midnight`() {
        val legacy = "record_type,id,date,systolic_mmhg,diastolic_mmhg,heart_rate_bpm,arm,note_type,note_details\n" +
            "NOTE,n9,2026-07-18,,,,,OTHER,old note\n"
        val parsed = Csv.parse(legacy)
        assertEquals(1, parsed.notes.size)
        assertEquals(LocalDate.parse("2026-07-18"), parsed.notes.single().date)
        assertEquals(LocalTime.of(0, 1), parsed.notes.single().time)
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
        assertEquals(1, parsed.skippedRows)
    }

    @Test
    fun `rows that could not be read are counted`() {
        val written = Csv.write(listOf(reading()), listOf(note())) +
            "READING,bad,not-a-date,x,y,z,NOPE,,\n" +
            "NOTE,n2,2026-07-18,,,,,NOT_A_TYPE,x\n" +
            "GARBAGE\n"
        assertEquals(3, Csv.parse(written).skippedRows)
    }

    @Test
    fun `a clean round trip skips nothing`() {
        val written = Csv.write(listOf(reading()), listOf(note()))
        assertEquals(0, Csv.parse(written).skippedRows)
    }

    @Test
    fun `a byte-order mark before the header is ignored`() {
        val bom = "\uFEFF"
        val legacy = bom + "id,taken_at,systolic_mmhg,diastolic_mmhg,heart_rate_bpm,arm\n" +
            "abc,2026-07-18T08:41:00Z,120,80,70,LEFT\n"
        assertEquals(1, Csv.parse(legacy).readings.size)
        assertEquals(1, Csv.parse(bom + Csv.write(listOf(reading()), emptyList())).readings.size)
        assertTrue(Csv.isBackupFile(legacy))
    }

    @Test
    fun `only this app's files count as backup files`() {
        assertTrue(Csv.isBackupFile(""))
        assertTrue(Csv.isBackupFile("\n\n"))
        assertTrue(Csv.isBackupFile(Csv.write(emptyList(), emptyList())))
        assertTrue(Csv.isBackupFile("id,taken_at,systolic_mmhg,diastolic_mmhg,heart_rate_bpm,arm\n"))
        assertFalse(Csv.isBackupFile("date,glucose_mmol\n2026-07-18,5.4\n"))
        assertFalse(Csv.isBackupFile("just some text"))
    }

    @Test
    fun `irregular heartbeat flag round trips`() {
        val flagged = reading(id = "f").copy(irregularHeartbeat = true)
        val plain = reading(id = "p")
        val parsed = Csv.parse(Csv.write(listOf(flagged, plain), listOf(note())))
        assertEquals(setOf(flagged, plain), parsed.readings.toSet())
        assertEquals(0, parsed.skippedRows)
    }

    @Test
    fun `a file written before the irregular heartbeat column reads cleanly`() {
        // Exactly what 2.4–2.6 wrote: nine columns, no irregular_heartbeat.
        val v2 = "record_type,id,date,systolic_mmhg,diastolic_mmhg,heart_rate_bpm,arm,note_type,note_details\n" +
            "READING,r1,2026-07-18T08:41:00Z,120,80,70,LEFT,,\n" +
            "NOTE,n1,2026-07-18T00:01,,,,,MEDICATION_CHANGED,plain details\n"
        val parsed = Csv.parse(v2)
        assertEquals(listOf(reading()), parsed.readings)
        assertEquals(listOf(note()), parsed.notes)
        assertEquals(0, parsed.skippedRows)
        assertTrue(Csv.isBackupFile(v2))
    }

    @Test
    fun `an unexpected irregular heartbeat value fails only that row`() {
        val written = Csv.write(listOf(reading()), emptyList()) +
            "READING,r2,2026-07-18T09:00:00Z,120,80,70,LEFT,,,maybe\n"
        val parsed = Csv.parse(written)
        assertEquals(1, parsed.readings.size)
        assertEquals(1, parsed.skippedRows)
    }

    @Test
    fun `carriage return at the start of details is guarded and round trips`() {
        val payload = "\rstarts with CR"
        val parsed = Csv.parse(Csv.write(emptyList(), listOf(note(details = payload))))
        assertEquals(payload, parsed.notes.single().details)
    }
}
