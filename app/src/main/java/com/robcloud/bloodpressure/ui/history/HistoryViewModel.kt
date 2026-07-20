package com.robcloud.bloodpressure.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.backup.BackupSyncWorker
import com.robcloud.bloodpressure.backup.Csv
import com.robcloud.bloodpressure.backup.NoBackupFolderSelectedException
import com.robcloud.bloodpressure.backup.StorageHost
import com.robcloud.bloodpressure.data.DeletedNote
import com.robcloud.bloodpressure.data.DeletedReading
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.report.ReportPdf
import com.robcloud.bloodpressure.widget.LastReadingWidgetProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Period(val label: String) {
    MONTH("Month"),
    QUARTER("Quarter"),
    YEAR("Year"),
    ALL("All time")
}

enum class SyncStatus { IDLE, SYNCING, ERROR }

private val STALE_BACKUP_THRESHOLD_DAYS = 7L

data class HistoryUiState(
    val period: Period = Period.MONTH,
    val readings: List<Reading> = emptyList(),
    val allReadings: List<Reading> = emptyList(),
    val allNotes: List<Note> = emptyList(),
    val totalReadingsCount: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val lastSyncedAt: Instant? = null,
    val syncError: String? = null,
    val backupFolderName: String? = null
) {
    /** True when a backup folder is set but hasn't synced in over a week (or ever). */
    val isBackupStale: Boolean
        get() = backupFolderName != null &&
            (lastSyncedAt == null || lastSyncedAt.isBefore(Instant.now().minus(STALE_BACKUP_THRESHOLD_DAYS, ChronoUnit.DAYS)))
}

private data class SyncMeta(
    val status: SyncStatus,
    val lastSyncedAt: Instant?,
    val error: String?,
    val folderName: String?
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BloodPressureApp
    private val dao = app.database.readingDao()
    private val noteDao = app.database.noteDao()

    private val period = MutableStateFlow(Period.MONTH)
    private val syncStatus = MutableStateFlow(SyncStatus.IDLE)
    private val lastSyncedAt = MutableStateFlow(app.backupFolderStore.getLastSyncedAt())
    private val syncError = MutableStateFlow<String?>(null)
    private val folderName = MutableStateFlow(app.backupFolderStore.displayName())

    /** One-shot user-facing messages (import/export results), consumed by a snackbar. */
    val message = MutableStateFlow<String?>(null)

    /** Set to a shareable report PDF Uri when one is ready; the screen launches the share sheet. */
    val pendingReportShare = MutableStateFlow<Uri?>(null)

    private val syncMeta = combine(syncStatus, lastSyncedAt, syncError, folderName) { status, synced, error, folder ->
        SyncMeta(status, synced, error, folder)
    }

    val uiState: StateFlow<HistoryUiState> =
        combine(dao.observeAll(), noteDao.observeAll(), period, syncMeta) { readings, notes, period, meta ->
            HistoryUiState(
                period = period,
                readings = filterByPeriod(readings, period),
                allReadings = readings,
                allNotes = notes,
                totalReadingsCount = readings.size,
                syncStatus = meta.status,
                lastSyncedAt = meta.lastSyncedAt,
                syncError = meta.error,
                backupFolderName = meta.folderName
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectPeriod(newPeriod: Period) {
        period.value = newPeriod
    }

    fun consumeMessage() {
        message.value = null
    }

    /** Builds a doctor's-report PDF for the readings/notes currently in view and readies it to share. */
    fun generateReport() {
        val snapshot = uiState.value
        if (snapshot.readings.isEmpty()) {
            message.value = "No readings in this period to report"
            return
        }
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    ReportPdf.generate(
                        app,
                        snapshot.period.label,
                        snapshot.readings,
                        filterNotesByPeriod(snapshot.allNotes, snapshot.period)
                    )
                }
                pendingReportShare.value = uri
            } catch (e: Exception) {
                message.value = "Report failed: ${e.message}"
            }
        }
    }

    fun consumeReportShare() {
        pendingReportShare.value = null
    }

    fun updateReading(reading: Reading) {
        viewModelScope.launch {
            dao.insert(reading)
            BackupSyncWorker.enqueue(app)
            LastReadingWidgetProvider.refresh(app)
        }
    }

    fun deleteReading(reading: Reading) {
        viewModelScope.launch {
            dao.deleteById(reading.id)
            dao.insertTombstone(DeletedReading(reading.id))
            BackupSyncWorker.enqueue(app)
            LastReadingWidgetProvider.refresh(app)
            message.value = "Reading deleted"
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            noteDao.insert(note)
            BackupSyncWorker.enqueue(app)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.deleteById(note.id)
            noteDao.insertTombstone(DeletedNote(note.id))
            BackupSyncWorker.enqueue(app)
            message.value = "Note deleted"
        }
    }

    fun exportCsvTo(folderUri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                val folder = DocumentFile.fromTreeUri(app, folderUri)
                    ?: error("Chosen folder is no longer accessible")
                folder.findFile(fileName)?.delete()
                val target = folder.createFile("text/csv", fileName)
                    ?: error("Could not create $fileName in the chosen folder")
                val readings = dao.getAll()
                val notes = noteDao.getAll()
                val csv = Csv.write(readings, notes)
                app.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                    output.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open the chosen file for writing")
                message.value = "Exported ${pluralize(readings.size, "reading")}, ${pluralize(notes.size, "note")}"
            } catch (e: Exception) {
                message.value = "Export failed: ${e.message}"
            }
        }
    }

    fun importCsv(storageHost: StorageHost) {
        viewModelScope.launch {
            val uri = storageHost.openDocument() ?: return@launch
            try {
                val text = app.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                    ?: error("Could not read the chosen file")
                val parsed = Csv.parse(text)
                if (parsed.readings.isEmpty() && parsed.notes.isEmpty()) {
                    message.value = "No readings or notes found in that file"
                    return@launch
                }
                dao.insertAll(parsed.readings)
                dao.clearTombstones(parsed.readings.map { it.id })
                noteDao.insertAll(parsed.notes)
                noteDao.clearTombstones(parsed.notes.map { it.id })
                BackupSyncWorker.enqueue(app)
                LastReadingWidgetProvider.refresh(app)
                message.value = "Imported ${pluralize(parsed.readings.size, "reading")}, ${pluralize(parsed.notes.size, "note")}"
            } catch (e: Exception) {
                message.value = "Import failed: ${e.message}"
            }
        }
    }

    fun chooseFolder(storageHost: StorageHost) {
        viewModelScope.launch {
            val uri = storageHost.pickFolder() ?: return@launch
            app.backupFolderStore.set(uri)
            folderName.value = app.backupFolderStore.displayName()
            syncNow()
        }
    }

    fun syncNow() {
        if (syncStatus.value == SyncStatus.SYNCING) return
        viewModelScope.launch {
            syncStatus.value = SyncStatus.SYNCING
            syncError.value = null
            try {
                val result = app.backupSyncManager.sync()
                lastSyncedAt.value = result.syncedAt
                syncStatus.value = SyncStatus.IDLE
            } catch (e: NoBackupFolderSelectedException) {
                syncStatus.value = SyncStatus.IDLE
            } catch (e: Exception) {
                syncError.value = e.message ?: "Sync failed"
                syncStatus.value = SyncStatus.ERROR
            }
        }
    }

}

private fun pluralize(count: Int, noun: String): String = "$count $noun${if (count == 1) "" else "s"}"

fun filterByPeriod(readings: List<Reading>, period: Period): List<Reading> {
    if (period == Period.ALL) return readings
    val cutoff = when (period) {
        Period.MONTH -> Instant.now().minus(30, ChronoUnit.DAYS)
        Period.QUARTER -> Instant.now().minus(91, ChronoUnit.DAYS)
        Period.YEAR -> Instant.now().minus(365, ChronoUnit.DAYS)
        Period.ALL -> Instant.EPOCH
    }
    return readings.filter { it.takenAt.isAfter(cutoff) }
}

fun filterNotesByPeriod(notes: List<Note>, period: Period): List<Note> {
    if (period == Period.ALL) return notes
    val cutoff = when (period) {
        Period.MONTH -> LocalDate.now().minusDays(30)
        Period.QUARTER -> LocalDate.now().minusDays(91)
        Period.YEAR -> LocalDate.now().minusDays(365)
        Period.ALL -> LocalDate.MIN
    }
    return notes.filter { !it.date.isBefore(cutoff) }
}
