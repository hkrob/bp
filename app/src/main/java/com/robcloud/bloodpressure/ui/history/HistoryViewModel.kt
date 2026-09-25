package com.robcloud.bloodpressure.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.backup.BackupStatus
import com.robcloud.bloodpressure.backup.BackupSyncWorker
import com.robcloud.bloodpressure.backup.Csv
import com.robcloud.bloodpressure.backup.NoBackupFolderSelectedException
import com.robcloud.bloodpressure.backup.SafFolder
import com.robcloud.bloodpressure.backup.planMerge
import com.robcloud.bloodpressure.backup.status
import com.robcloud.bloodpressure.backup.unreadableRowsNotice
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.report.ReportPdf
import com.robcloud.bloodpressure.widget.LastReadingWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Period(val label: String) {
    MONTH("Month"),
    QUARTER("Quarter"),
    YEAR("Year"),
    SINCE_CHECKUP("Since Check Up"),
    ALL("All time")
}

private val STALE_BACKUP_THRESHOLD_DAYS = 7L

data class HistoryUiState(
    val period: Period = Period.MONTH,
    val readings: List<Reading> = emptyList(),
    val allReadings: List<Reading> = emptyList(),
    val allNotes: List<Note> = emptyList(),
    val totalReadingsCount: Int = 0,
    val syncing: Boolean = false,
    val backup: BackupStatus = BackupStatus()
) {
    /** True when a backup folder is set but hasn't synced in over a week (or ever). */
    val isBackupStale: Boolean
        get() = backup.configured &&
            (backup.lastSyncedAt == null ||
                backup.lastSyncedAt.isBefore(Instant.now().minus(STALE_BACKUP_THRESHOLD_DAYS, ChronoUnit.DAYS)))
}

/**
 * Shared by the History and Log tabs (both use the Activity-scoped instance). Every storage
 * provider call runs on [Dispatchers.IO]: for Google Drive they can block on the network.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BloodPressureApp
    private val dao = app.database.readingDao()
    private val noteDao = app.database.noteDao()
    private val store = app.backupFolderStore

    private val period = MutableStateFlow(Period.MONTH)
    private val syncing = MutableStateFlow(false)

    // Follows the stored status, so background syncs (after-save, daily) show up here too.
    private val backupStatus = store.changes().map { store.status() }.flowOn(Dispatchers.IO)

    /** One-shot user-facing messages (import/export results), consumed by a snackbar. */
    val message = MutableStateFlow<String?>(null)

    /** Set to a shareable report PDF Uri when one is ready; the screen launches the share sheet. */
    val pendingReportShare = MutableStateFlow<Uri?>(null)

    val uiState: StateFlow<HistoryUiState> =
        combine(dao.observeAll(), noteDao.observeAll(), period, syncing, backupStatus) { readings, notes, period, syncing, backup ->
            HistoryUiState(
                period = period,
                readings = filterByPeriod(readings, period, notes),
                allReadings = readings,
                allNotes = notes,
                totalReadingsCount = readings.size,
                syncing = syncing,
                backup = backup
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Refreshes the cached folder name from the provider (the status flow picks it up).
            store.displayName()
            store.takePendingNotice()?.let { message.value = it }
        }
    }

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
            } catch (e: CancellationException) {
                throw e
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
            dao.deleteWithTombstone(reading.id)
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
            noteDao.deleteWithTombstone(note.id)
            BackupSyncWorker.enqueue(app)
            message.value = "Note deleted"
        }
    }

    /** Writes every reading and note to [fileName] in [folderUri], replacing a file of that name. */
    fun exportCsvTo(folderUri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                val readings = dao.getAll()
                val notes = noteDao.getAll()
                if (readings.isEmpty() && notes.isEmpty()) {
                    message.value = "Nothing to export yet"
                    return@launch
                }
                val replaced = withContext(Dispatchers.IO) {
                    val folder = SafFolder(app.contentResolver, folderUri)
                    val csv = Csv.write(readings, notes)
                    // Write into an existing file rather than delete-then-create, so there is
                    // never a moment with no file (and no duplicate names on Drive).
                    val existing = folder.find(fileName).firstOrNull()
                    if (existing != null) folder.overwrite(existing, csv) else folder.create(fileName, csv)
                    existing != null
                }
                val what = "${pluralize(readings.size, "reading")}, ${pluralize(notes.size, "note")}"
                message.value = if (replaced) "Exported $what, replacing the old $fileName" else "Exported $what"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = "Export failed: ${e.message}"
            }
        }
    }

    /**
     * Restores readings and notes from a CSV file. Adds only rows that aren't already here, so it
     * never reverts a later edit; rows deleted here since the file was written come back, since
     * bringing them back is the point of importing an older file.
     */
    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val text = app.contentResolver.openInputStream(uri)?.use { it.reader(Charsets.UTF_8).readText() }
                        ?: error("Could not read the chosen file")
                    Csv.parse(text)
                }
                if (parsed.readings.isEmpty() && parsed.notes.isEmpty()) {
                    message.value = "No readings or notes found in that file"
                    return@launch
                }
                val (newReadings, newNotes) = app.database.withTransaction {
                    val readingPlan = planMerge(dao.getAll(), parsed.readings, emptySet()) { it.id }
                    val notePlan = planMerge(noteDao.getAll(), parsed.notes, emptySet()) { it.id }
                    dao.insertAllIfAbsent(readingPlan.toInsert)
                    dao.clearTombstonesChunked(readingPlan.toInsert.map { it.id })
                    noteDao.insertAllIfAbsent(notePlan.toInsert)
                    noteDao.clearTombstonesChunked(notePlan.toInsert.map { it.id })
                    readingPlan.toInsert.size to notePlan.toInsert.size
                }
                if (newReadings + newNotes > 0) {
                    BackupSyncWorker.enqueue(app)
                    LastReadingWidgetProvider.refresh(app)
                }
                message.value = importMessage(
                    newReadings, newNotes,
                    alreadyHere = parsed.readings.size + parsed.notes.size - newReadings - newNotes,
                    unreadable = parsed.skippedRows
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = "Import failed: ${e.message}"
            }
        }
    }

    /** Makes [uri] (just picked in the system folder picker) the backup folder and syncs to it. */
    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    store.set(uri)
                    store.displayName()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = "Couldn't use that folder: ${e.message}"
                return@launch
            }
            syncNow()
        }
    }

    fun syncNow() {
        if (syncing.value) return
        syncing.value = true
        viewModelScope.launch {
            try {
                val result = app.backupSyncManager.sync()
                result.preservedCopy?.let { message.value = unreadableRowsNotice(it) }
            } catch (e: NoBackupFolderSelectedException) {
                // Nothing to sync to yet.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Persisted so the status line (and the Add reading banner, if it lasts) show it.
                store.recordFailure(e.message ?: "Sync failed")
            } finally {
                syncing.value = false
            }
        }
    }
}

private fun pluralize(count: Int, noun: String): String = "$count $noun${if (count == 1) "" else "s"}"

internal fun importMessage(newReadings: Int, newNotes: Int, alreadyHere: Int, unreadable: Int): String {
    val main = if (newReadings + newNotes == 0) {
        "Nothing new to import — everything in that file is already here"
    } else {
        "Imported ${pluralize(newReadings, "reading")}, ${pluralize(newNotes, "note")}" +
            if (alreadyHere > 0) " ($alreadyHere already here)" else ""
    }
    return main + if (unreadable > 0) ". ${pluralize(unreadable, "row")} couldn't be read" else ""
}

/** Most recent Check Up note's date, or null if none has ever been logged. */
private fun lastCheckUpDate(notes: List<Note>): LocalDate? =
    notes.filter { it.noteType == NoteType.CHECK_UP }.maxOfOrNull { it.date }

fun filterByPeriod(readings: List<Reading>, period: Period, notes: List<Note> = emptyList()): List<Reading> {
    if (period == Period.ALL) return readings
    val cutoff = when (period) {
        Period.MONTH -> Instant.now().minus(30, ChronoUnit.DAYS)
        Period.QUARTER -> Instant.now().minus(91, ChronoUnit.DAYS)
        Period.YEAR -> Instant.now().minus(365, ChronoUnit.DAYS)
        // No Check Up logged yet — nothing can be "since" it, so match none rather than everything.
        Period.SINCE_CHECKUP -> lastCheckUpDate(notes)
            ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()
            ?: Instant.MAX
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
        Period.SINCE_CHECKUP -> lastCheckUpDate(notes) ?: LocalDate.MAX
        Period.ALL -> LocalDate.MIN
    }
    return notes.filter { !it.date.isBefore(cutoff) }
}
