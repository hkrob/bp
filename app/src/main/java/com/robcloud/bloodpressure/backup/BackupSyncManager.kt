package com.robcloud.bloodpressure.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.robcloud.bloodpressure.data.NoteDao
import com.robcloud.bloodpressure.data.ReadingDao
import com.robcloud.bloodpressure.widget.LastReadingWidgetProvider
import java.time.Instant

private const val FILE_NAME = "readings.csv"
private const val CSV_MIME = "text/csv"

class NoBackupFolderSelectedException : Exception("Choose a backup folder first")

data class SyncResult(val totalReadings: Int, val syncedAt: Instant)

/**
 * Keeps a single human-readable CSV file ("readings.csv") in a folder the user picked via
 * the system folder picker as a mirror of the local Room database. Every sync merges local +
 * existing-file readings and notes by id (neither is edited after capture, so a plain union
 * is enough) and rewrites the whole file — simplest approach that avoids partial-write conflicts.
 */
class BackupSyncManager(
    private val context: Context,
    private val readingDao: ReadingDao,
    private val noteDao: NoteDao,
    private val folderStore: BackupFolderStore
) {
    suspend fun sync(): SyncResult {
        val folderUri = folderStore.get() ?: throw NoBackupFolderSelectedException()
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IllegalStateException("Backup folder is no longer accessible")

        val existingFile = folder.findFile(FILE_NAME)
        val remote = existingFile?.let(::readCsv) ?: ParsedCsv(emptyList(), emptyList())
        val localReadings = readingDao.getAll()
        val localNotes = noteDao.getAll()
        val readingTombstones = readingDao.getTombstoneIds().toSet()
        val noteTombstones = noteDao.getTombstoneIds().toSet()

        // Local entries come last so an edited local row wins over the CSV copy;
        // tombstoned ids are excluded so deletions propagate instead of resurrecting.
        val mergedReadings = (remote.readings.filterNot { it.id in readingTombstones } + localReadings)
            .associateBy { it.id }
            .values
            .toList()
        val mergedNotes = (remote.notes.filterNot { it.id in noteTombstones } + localNotes)
            .associateBy { it.id }
            .values
            .toList()

        readingDao.insertAll(mergedReadings)
        noteDao.insertAll(mergedNotes)
        writeCsv(folder, existingFile, Csv.write(mergedReadings, mergedNotes))
        LastReadingWidgetProvider.refresh(context)

        val syncedAt = Instant.now()
        folderStore.setLastSyncedAt(syncedAt)
        return SyncResult(totalReadings = mergedReadings.size, syncedAt = syncedAt)
    }

    private fun readCsv(file: DocumentFile): ParsedCsv {
        val text = context.contentResolver.openInputStream(file.uri)?.use { it.reader().readText() }
            ?: return ParsedCsv(emptyList(), emptyList())
        return Csv.parse(text)
    }

    private fun writeCsv(folder: DocumentFile, existingFile: DocumentFile?, csv: String) {
        val target = existingFile ?: folder.createFile(CSV_MIME, FILE_NAME)
            ?: error("Could not create $FILE_NAME in the chosen folder")
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
            output.write(csv.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open $FILE_NAME for writing")
    }
}
