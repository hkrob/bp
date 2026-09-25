package com.robcloud.bloodpressure.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergePlanTest {

    private data class Row(val id: String, val value: Int)

    private fun plan(local: List<Row>, remote: List<Row>, tombstones: Set<String> = emptySet()) =
        planMerge(local, remote, tombstones) { it.id }

    @Test
    fun `rows only in the file are inserted and written back`() {
        val result = plan(local = listOf(Row("a", 1)), remote = listOf(Row("a", 1), Row("b", 2)))
        assertEquals(listOf(Row("b", 2)), result.toInsert)
        assertEquals(setOf(Row("a", 1), Row("b", 2)), result.merged.toSet())
    }

    @Test
    fun `a local edit is never overwritten by the file's older copy`() {
        val result = plan(local = listOf(Row("a", 135)), remote = listOf(Row("a", 185)))
        assertTrue(result.toInsert.isEmpty())
        assertEquals(listOf(Row("a", 135)), result.merged)
    }

    @Test
    fun `tombstoned rows are dropped from the file instead of resurrected`() {
        val result = plan(local = emptyList(), remote = listOf(Row("gone", 1), Row("kept", 2)), tombstones = setOf("gone"))
        assertEquals(listOf(Row("kept", 2)), result.toInsert)
        assertEquals(listOf(Row("kept", 2)), result.merged)
    }

    @Test
    fun `duplicate ids across files are inserted once`() {
        val result = plan(local = emptyList(), remote = listOf(Row("x", 1), Row("x", 1)))
        assertEquals(listOf(Row("x", 1)), result.toInsert)
        assertEquals(1, result.merged.size)
    }

    @Test
    fun `empty file leaves local rows as the whole backup`() {
        val local = listOf(Row("a", 1), Row("b", 2))
        val result = plan(local = local, remote = emptyList())
        assertTrue(result.toInsert.isEmpty())
        assertEquals(local, result.merged)
    }
}
