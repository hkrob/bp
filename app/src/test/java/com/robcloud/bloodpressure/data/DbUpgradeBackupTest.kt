package com.robcloud.bloodpressure.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class DbUpgradeBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-09-25T09:00:00Z")

    /** A file with a valid SQLite header claiming [userVersion], padded like a real database page. */
    private fun sqliteFile(userVersion: Int, name: String = "bp-tracker.db"): File {
        val header = ByteArray(4096)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(header)
        header[60] = (userVersion ushr 24).toByte()
        header[61] = (userVersion ushr 16).toByte()
        header[62] = (userVersion ushr 8).toByte()
        header[63] = userVersion.toByte()
        return tmp.newFile(name).apply { writeBytes(header) }
    }

    @Test
    fun `reads the schema version from the header`() {
        assertEquals(4, readSqliteUserVersion(sqliteFile(4)))
        assertEquals(300, readSqliteUserVersion(sqliteFile(300, "big.db")))
    }

    @Test
    fun `missing, short or non-SQLite files have no version`() {
        assertNull(readSqliteUserVersion(File(tmp.root, "absent.db")))
        assertNull(readSqliteUserVersion(tmp.newFile("short.db").apply { writeBytes(ByteArray(10)) }))
        assertNull(readSqliteUserVersion(tmp.newFile("text.db").apply { writeBytes(ByteArray(100) { 'x'.code.toByte() }) }))
    }

    @Test
    fun `copies the database before an upgrade`() {
        val db = sqliteFile(4)
        val backups = File(tmp.root, "db-backups")
        val copy = backupBeforeUpgrade(db, backups, targetVersion = 5, now = now)
        assertNotNull(copy)
        assertEquals("bp-tracker-v4-20260925-090000.db", copy!!.name)
        assertTrue(copy.readBytes().contentEquals(db.readBytes()))
    }

    @Test
    fun `no copy when already current, or on a fresh install`() {
        val backups = File(tmp.root, "db-backups")
        assertNull(backupBeforeUpgrade(sqliteFile(5), backups, targetVersion = 5, now = now))
        assertNull(backupBeforeUpgrade(File(tmp.root, "none.db"), backups, targetVersion = 5, now = now))
        assertFalse(backups.exists())
    }

    @Test
    fun `a non-empty rollback journal is copied alongside, an empty one is not`() {
        val db = sqliteFile(4)
        val backups = File(tmp.root, "db-backups")
        File(db.path + "-journal").writeBytes(ByteArray(0))
        backupBeforeUpgrade(db, backups, targetVersion = 5, now = now)
        assertFalse(File(backups, "bp-tracker-v4-20260925-090000.db-journal").exists())

        File(db.path + "-journal").writeBytes(ByteArray(512) { 1 })
        backupBeforeUpgrade(db, backups, targetVersion = 5, now = now.plusSeconds(1))
        assertTrue(File(backups, "bp-tracker-v4-20260925-090001.db-journal").exists())
    }

    @Test
    fun `keeps only the newest copies`() {
        val db = sqliteFile(4)
        val backups = File(tmp.root, "db-backups")
        for (i in 0 until 5) backupBeforeUpgrade(db, backups, targetVersion = 5, now = now.plusSeconds(i * 60L), keep = 3)
        val names = backups.listFiles()!!.map { it.name }.sorted()
        assertEquals(
            listOf("bp-tracker-v4-20260925-090200.db", "bp-tracker-v4-20260925-090300.db", "bp-tracker-v4-20260925-090400.db"),
            names
        )
    }
}
