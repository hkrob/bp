package com.robcloud.bloodpressure.data

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

/**
 * The schema version Room stamps into the SQLite header (`PRAGMA user_version`, a big-endian
 * int at byte 60), read without opening the database. Null if the file is missing, too short,
 * or not SQLite.
 */
fun readSqliteUserVersion(file: File): Int? {
    if (!file.isFile || file.length() < 64) return null
    val header = ByteArray(64)
    RandomAccessFile(file, "r").use { it.readFully(header) }
    if (!header.copyOfRange(0, SQLITE_MAGIC.size).contentEquals(SQLITE_MAGIC)) return null
    return ((header[60].toInt() and 0xFF) shl 24) or
        ((header[61].toInt() and 0xFF) shl 16) or
        ((header[62].toInt() and 0xFF) shl 8) or
        (header[63].toInt() and 0xFF)
}

/**
 * Copies [dbFile] into [backupDir] when its on-disk schema version differs from [targetVersion],
 * i.e. just before Room migrates it. A non-empty rollback journal is copied alongside (in
 * TRUNCATE mode an empty journal is normal; a non-empty one holds uncommitted pages SQLite needs
 * to recover). Keeps only the newest [keep] copies. Returns the copy, or null if none was needed.
 */
fun backupBeforeUpgrade(
    dbFile: File,
    backupDir: File,
    targetVersion: Int,
    now: Instant,
    keep: Int = 3
): File? {
    val onDisk = readSqliteUserVersion(dbFile) ?: return null
    if (onDisk <= 0 || onDisk == targetVersion) return null

    backupDir.mkdirs()
    val base = "${dbFile.nameWithoutExtension}-v$onDisk-${STAMP.format(now)}"
    val copy = File(backupDir, "$base.db")
    dbFile.copyTo(copy, overwrite = true)
    val journal = File(dbFile.path + "-journal")
    if (journal.isFile && journal.length() > 0) {
        journal.copyTo(File(backupDir, "$base.db-journal"), overwrite = true)
    }

    backupDir.listFiles { f -> f.name.endsWith(".db") }
        ?.sortedByDescending { it.name.substringAfterLast("-v").substringAfter('-') }
        ?.drop(keep)
        ?.forEach { old ->
            old.delete()
            File(old.path + "-journal").delete()
        }
    return copy
}
