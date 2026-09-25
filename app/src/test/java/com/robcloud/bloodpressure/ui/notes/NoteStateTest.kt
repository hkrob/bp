package com.robcloud.bloodpressure.ui.notes

import com.robcloud.bloodpressure.data.DEFAULT_NOTE_TIME
import com.robcloud.bloodpressure.data.NoteType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NoteStateTest {

    private val now = LocalTime.of(16, 24)

    @Test
    fun `medication taken records the time it was logged`() {
        assertEquals(now, NoteUiState(noteType = NoteType.MEDICATION_TAKEN).noteTime(now))
    }

    @Test
    fun `medication taken keeps a picked time`() {
        val picked = LocalTime.of(7, 30)
        val state = NoteUiState(noteType = NoteType.MEDICATION_TAKEN, time = picked, timeEdited = true)
        assertEquals(picked, state.noteTime(now))
    }

    @Test
    fun `a half-written note survives the process being killed`() {
        val typed = NoteUiState(
            date = LocalDate.of(2026, 9, 20), dateEdited = true,
            time = LocalTime.of(7, 45), timeEdited = true,
            noteType = NoteType.MEDICATION_TAKEN, details = "half a tablet"
        )
        val saved = typed.toSavedEntry()
        val restored = NoteUiState(date = LocalDate.of(2026, 9, 25), time = now).withSavedEntry { saved[it] }
        assertEquals(typed, restored)
    }

    @Test
    fun `an unpicked date and time follow the clock after a restore`() {
        val saved = NoteUiState(date = LocalDate.of(2026, 9, 20), time = LocalTime.of(7, 45), details = "x").toSavedEntry()
        val fresh = NoteUiState(date = LocalDate.of(2026, 9, 25), time = now)
        assertEquals(fresh.copy(details = "x"), fresh.withSavedEntry { saved[it] })
    }

    @Test
    fun `other note types use the sort placeholder`() {
        for (type in NoteType.entries - NoteType.MEDICATION_TAKEN) {
            assertEquals(DEFAULT_NOTE_TIME, NoteUiState(noteType = type, time = LocalTime.NOON, timeEdited = true).noteTime(now))
        }
    }
}
