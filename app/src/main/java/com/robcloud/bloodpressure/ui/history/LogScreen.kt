package com.robcloud.bloodpressure.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robcloud.bloodpressure.data.Arm
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.ui.EqualWidthSegmentedRow
import com.robcloud.bloodpressure.ui.notes.EditNoteDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yy HH:mm").withZone(ZoneId.systemDefault())

private enum class ArmFilter(val label: String) {
    ALL("All"),
    LEFT("L"),
    RIGHT("R")
}

private sealed class LogEntry(val sortInstant: Instant) {
    data class ReadingEntry(val reading: Reading) : LogEntry(reading.takenAt)
    data class NoteEntry(val note: Note) :
        LogEntry(note.date.atTime(note.time).atZone(ZoneId.systemDefault()).toInstant())
}

/**
 * Information-dense, text-only log: one fixed-width line per reading, newest first.
 * Supports its own period + arm filters, independent of the History tab's period.
 * Notes can be toggled in/out, interleaved by date; reading rows are tappable for edit/delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: HistoryViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editingReading by remember { mutableStateOf<Reading?>(null) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var period by remember { mutableStateOf(Period.ALL) }
    var armFilter by remember { mutableStateOf(ArmFilter.ALL) }
    var showNotes by remember { mutableStateOf(false) }
    var showMeds by remember { mutableStateOf(true) }

    editingReading?.let { reading ->
        EditReadingDialog(
            reading = reading,
            onDismiss = { editingReading = null },
            onSave = { updated ->
                viewModel.updateReading(updated)
                editingReading = null
            },
            onDelete = { toDelete ->
                viewModel.deleteReading(toDelete)
                editingReading = null
            }
        )
    }

    editingNote?.let { note ->
        EditNoteDialog(
            note = note,
            onDismiss = { editingNote = null },
            onSave = { updated ->
                viewModel.updateNote(updated)
                editingNote = null
            },
            onDelete = { toDelete ->
                viewModel.deleteNote(toDelete)
                editingNote = null
            }
        )
    }

    val filteredReadings = remember(state.allReadings, period, armFilter) {
        filterByPeriod(state.allReadings, period).filter { reading ->
            when (armFilter) {
                ArmFilter.ALL -> true
                ArmFilter.LEFT -> reading.arm == Arm.LEFT
                ArmFilter.RIGHT -> reading.arm == Arm.RIGHT
            }
        }
    }
    val filteredNotes = remember(state.allNotes, period, showNotes, showMeds) {
        filterNotesByPeriod(state.allNotes, period).filter { note ->
            if (note.noteType == NoteType.MEDICATION_TAKEN) showMeds else showNotes
        }
    }
    val entries = remember(filteredReadings, filteredNotes) {
        (filteredReadings.map { LogEntry.ReadingEntry(it) } + filteredNotes.map { LogEntry.NoteEntry(it) })
            .sortedByDescending { it.sortInstant }
    }

    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqualWidthSegmentedRow(
                options = Period.entries,
                selected = period,
                label = { it.label },
                onSelect = { period = it },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SingleChoiceSegmentedButtonRow {
                ArmFilter.entries.forEachIndexed { index, filter ->
                    SegmentedButton(
                        selected = armFilter == filter,
                        onClick = { armFilter = filter },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ArmFilter.entries.size)
                    ) {
                        Text(filter.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                "${filteredReadings.size} ${if (filteredReadings.size == 1) "reading" else "readings"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show notes", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = showNotes, onCheckedChange = { showNotes = it })
            Text("Show meds", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            Switch(checked = showMeds, onCheckedChange = { showMeds = it })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                "DATE     TIME    SYS/DIA  HR ARM",
                style = mono.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "No readings match this filter",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(
                entries,
                key = { entry ->
                    when (entry) {
                        is LogEntry.ReadingEntry -> "r-${entry.reading.id}"
                        is LogEntry.NoteEntry -> "n-${entry.note.id}"
                    }
                }
            ) { entry ->
                when (entry) {
                    is LogEntry.ReadingEntry ->
                        LogRow(entry.reading, mono, onClick = { editingReading = entry.reading })
                    is LogEntry.NoteEntry ->
                        NoteLogRow(entry.note, mono, onClick = { editingNote = entry.note })
                }
            }
        }
    }
}

@Composable
private fun LogRow(
    reading: Reading,
    mono: TextStyle,
    onClick: () -> Unit
) {
    val dateTime = logDateFormatter.format(reading.takenAt)
    val bp = "${reading.systolicMmHg}/${reading.diastolicMmHg}".padStart(7)
    val hr = reading.heartRateBpm.toString().padStart(3)
    val arm = if (reading.arm == Arm.LEFT) "L" else "R"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text("$dateTime ", style = mono)
        Text(bp, style = mono.copy(fontWeight = FontWeight.Medium), color = readingStatusColor(reading))
        Text(" $hr  $arm", style = mono)
    }
}

/**
 * Same one-line, fixed-width shape as [LogRow] — the note shares the DATE/TIME columns so it lines
 * up with readings, and is set in the muted variant colour with a `[TYPE]` tag as the only cue.
 * The content is the note's details, or the type label when there are none (e.g. Medication Taken).
 */
@Composable
private fun NoteLogRow(note: Note, mono: TextStyle, onClick: () -> Unit) {
    val instant = note.date.atTime(note.time).atZone(ZoneId.systemDefault()).toInstant()
    val dateTime = logDateFormatter.format(instant)
    val content = note.details.trim().ifEmpty { note.noteType.label }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text("$dateTime ", style = mono)
        Text(
            "[${note.noteType.abbreviation}] $content",
            style = mono.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
