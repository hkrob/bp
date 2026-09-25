package com.robcloud.bloodpressure.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportMessageTest {

    @Test
    fun `reports what was new and what was already there`() {
        assertEquals("Imported 3 readings, 1 note (40 already here)", importMessage(3, 1, alreadyHere = 40, unreadable = 0))
        assertEquals("Imported 1 reading, 0 notes", importMessage(1, 0, alreadyHere = 0, unreadable = 0))
    }

    @Test
    fun `says so when nothing was new`() {
        assertEquals(
            "Nothing new to import — everything in that file is already here",
            importMessage(0, 0, alreadyHere = 12, unreadable = 0)
        )
    }

    @Test
    fun `mentions rows that could not be read`() {
        assertEquals("Imported 2 readings, 0 notes. 1 row couldn't be read", importMessage(2, 0, alreadyHere = 0, unreadable = 1))
    }
}
