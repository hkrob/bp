package com.robcloud.bloodpressure.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SnapshotPolicyTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun names(vararg dates: LocalDate) = dates.map(::snapshotName)

    @Test
    fun `snapshot names round trip`() {
        assertEquals("readings-2026-09-25.csv", snapshotName(today))
        assertEquals(today, parseSnapshotDate("readings-2026-09-25.csv"))
        assertNull(parseSnapshotDate("readings.csv"))
        assertNull(parseSnapshotDate("readings-unreadable-20260925-101500-1.csv"))
        assertNull(parseSnapshotDate("notes-2026-09-25.csv"))
    }

    @Test
    fun `everything from the last fourteen days is kept`() {
        val recent = (0L..13L).map { today.minusDays(it) }
        assertTrue(snapshotsToDelete(names(*recent.toTypedArray()), today).isEmpty())
    }

    @Test
    fun `older snapshots keep only the earliest of each month`() {
        val all = names(
            today.minusDays(14),            // 11 Sep: older than 14 days, not first of September
            LocalDate.of(2026, 9, 1),       // first of September: kept
            LocalDate.of(2026, 8, 3),       // first of August: kept
            LocalDate.of(2026, 8, 20)       // later in August: deleted
        )
        assertEquals(
            setOf(snapshotName(today.minusDays(14)), snapshotName(LocalDate.of(2026, 8, 20))),
            snapshotsToDelete(all, today).toSet()
        )
    }

    @Test
    fun `monthly copies older than twelve months are deleted`() {
        val keptMonth = LocalDate.of(2025, 10, 5)   // 12th month back, inclusive
        val droppedMonth = LocalDate.of(2025, 9, 30) // 13th month back
        assertEquals(listOf(snapshotName(droppedMonth)), snapshotsToDelete(names(keptMonth, droppedMonth), today))
    }

    @Test
    fun `files that are not snapshots are never deleted`() {
        val result = snapshotsToDelete(listOf("readings.csv", "notes.txt", "readings-2020-13-45.csv"), today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty folder deletes nothing`() {
        assertTrue(snapshotsToDelete(emptyList(), today).isEmpty())
    }
}
