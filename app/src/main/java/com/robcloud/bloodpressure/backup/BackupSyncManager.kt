package com.robcloud.bloodpressure.backup

import android.content.Context
import androidx.room.withTransaction
import com.robcloud.bloodpressure.BuildConfig
import com.robcloud.bloodpressure.data.AppDatabase
import com.robcloud.bloodpressure.widget.LastReadingWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// The debug build installs alongside the release app with its own database; giving it its own
// file stops test readings being merged into the real history if both point at the same folder.
private val FILE_NAME = if (BuildConfig.DEBUG) "readings-debug.csv" else "readings.csv"
private val SNAPSHOT_DIR = if (BuildConfig.DEBUG) "snapshots-debug" else "snapshots"
private val COPY_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

class NoBackupFolderSelectedException : Exception("Choose a backup folder first")

/** The folder is still configured but its SAF grant is gone (restored to a new phone, or revoked). */
class BackupFolderAccessLostException :
    Exception("Access to the backup folder was lost (for example after moving to a new phone). Choose the folder again.")

/**
 * The folder holds a same-named file this version can't recognise — written by another app, or
 * in a newer backup format — so it is left untouched rather than overwritten.
 */
class ForeignBackupFileException(name: String) :
    Exception("$name in that folder isn't a backup this version of BP Tracker can read, so it was left alone. Choose a different folder, or update the app.")

/**
 * [preservedCopy] names the copy made of a backup file whose rows couldn't all be read, if any.
 */
data class SyncResult(val totalReadings: Int, val syncedAt: Instant, val preservedCopy: String? = null)

/**
 * Keeps a single human-readable CSV file ("readings.csv") in a folder the user picked via
 * the system folder picker as a mirror of the local Room database. Every sync adds rows that
 * only the file has to the database (local rows always win, deletions propagate via
 * tombstones) and rewrites the whole file from the result. Once a day the previous file is
 * also kept as a dated snapshot, so a mistake that reaches the backup can still be undone.
 */
class BackupSyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val folderStore: BackupFolderStore
) {
    private val readingDao = database.readingDao()
    private val noteDao = database.noteDao()

    // After-save, daily and manual syncs can overlap; interleaved they could create duplicate
    // files or write an older merge over a newer one.
    private val mutex = Mutex()

    suspend fun sync(): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) { syncLocked() }
    }

    private suspend fun syncLocked(): SyncResult {
        val folderUri = folderStore.get() ?: throw NoBackupFolderSelectedException()
        if (!folderStore.hasAccess()) throw BackupFolderAccessLostException()
        val folder = SafFolder(context.contentResolver, folderUri)

        // Normally one file; duplicates left by older versions are all merged so nothing in
        // them is lost, and the first (by document id) stays the one that is written.
        val existing = folder.find(FILE_NAME)
        val remoteTexts = existing.map { folder.readText(it) }
        if (remoteTexts.any { !Csv.isBackupFile(it) }) throw ForeignBackupFileException(FILE_NAME)
        val parsed = remoteTexts.map(Csv::parse)

        // Never overwrite rows this version couldn't read: keep the original first, and if
        // even that fails, stop before anything is rewritten.
        var preservedCopy: String? = null
        parsed.forEachIndexed { i, p ->
            if (p.skippedRows > 0) {
                val name = "readings-unreadable-${COPY_STAMP.format(Instant.now())}-${i + 1}.csv"
                folder.create(name, remoteTexts[i])
                preservedCopy = name
            }
        }

        val (readings, notes) = database.withTransaction {
            val readingPlan = planMerge(
                readingDao.getAll(), parsed.flatMap { it.readings }, readingDao.getTombstoneIds().toSet()
            ) { it.id }
            val notePlan = planMerge(
                noteDao.getAll(), parsed.flatMap { it.notes }, noteDao.getTombstoneIds().toSet()
            ) { it.id }
            readingDao.insertAllIfAbsent(readingPlan.toInsert)
            noteDao.insertAllIfAbsent(notePlan.toInsert)
            readingPlan.merged to notePlan.merged
        }

        val target = existing.firstOrNull()
        if (target != null) {
            // Best effort: a provider that can't make folders mustn't block the backup itself.
            runCatching { snapshotIfDue(folder, remoteTexts.first()) }
        }
        val csv = Csv.write(readings, notes)
        if (target != null) folder.overwrite(target, csv) else folder.create(FILE_NAME, csv)
        LastReadingWidgetProvider.refresh(context)

        val syncedAt = Instant.now()
        folderStore.setLastSyncedAt(syncedAt)
        return SyncResult(totalReadings = readings.size, syncedAt = syncedAt, preservedCopy = preservedCopy)
    }

    /**
     * Once per day, before the first overwrite, copies the backup file as it stood into the
     * snapshots folder, then prunes old snapshots (see [snapshotsToDelete]).
     */
    private fun snapshotIfDue(folder: SafFolder, previous: String) {
        val today = LocalDate.now()
        if (folderStore.getLastSnapshotDate() == today) return
        val dir = folder.findOrCreateDirectory(SNAPSHOT_DIR)
        val name = snapshotName(today)
        if (dir.find(name).isEmpty()) dir.create(name, previous)
        val files = dir.children().filter { !it.isDirectory }
        val stale = snapshotsToDelete(files.map { it.name }, today).toSet()
        files.filter { it.name in stale }.forEach { runCatching { dir.delete(it) } }
        folderStore.setLastSnapshotDate(today)
    }
}
