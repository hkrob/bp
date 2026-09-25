package com.robcloud.bloodpressure.ui.notes

import com.robcloud.bloodpressure.data.DEFAULT_NOTE_TIME
import com.robcloud.bloodpressure.data.NoteType
import org.junit.Assert.assertEquals
import org.junit.Test
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
    fun `other note types use the sort placeholder`() {
        for (type in NoteType.entries - NoteType.MEDICATION_TAKEN) {
            assertEquals(DEFAULT_NOTE_TIME, NoteUiState(noteType = type, time = LocalTime.NOON, timeEdited = true).noteTime(now))
        }
    }
}
