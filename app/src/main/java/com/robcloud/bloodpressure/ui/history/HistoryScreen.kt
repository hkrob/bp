package com.robcloud.bloodpressure.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robcloud.bloodpressure.backup.StorageHost
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.ui.theme.StatusElevated
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(storageHost: StorageHost, viewModel: HistoryViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val pendingReportShare by viewModel.pendingReportShare.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editingReading by remember { mutableStateOf<Reading?>(null) }
    var exportDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(pendingReportShare) {
        pendingReportShare?.let { uri ->
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Blood Pressure Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Share report"))
            viewModel.consumeReportShare()
        }
    }

    if (exportDialogOpen) {
        ExportCsvDialog(
            storageHost = storageHost,
            defaultFileName = "bloodPressureReadings",
            onDismiss = { exportDialogOpen = false },
            onExport = { folderUri, fileName ->
                exportDialogOpen = false
                viewModel.exportCsvTo(folderUri, fileName)
            }
        )
    }

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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("History", style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.importCsv(storageHost) }) {
                            Icon(Icons.Filled.FileUpload, contentDescription = "Import CSV")
                        }
                        IconButton(onClick = { exportDialogOpen = true }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Export CSV")
                        }
                        IconButton(onClick = { viewModel.generateReport() }) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Share PDF report")
                        }
                        SyncButton(
                            state,
                            onClick = {
                                if (state.backupFolderName == null) {
                                    viewModel.chooseFolder(storageHost)
                                } else {
                                    viewModel.syncNow()
                                }
                            }
                        )
                    }
                }

                SyncStatusLine(state)

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Period.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = state.period == period,
                            onClick = { viewModel.selectPeriod(period) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Period.entries.size)
                        ) {
                            Text(period.label)
                        }
                    }
                }

                PeriodStatsRow(state.readings)

                ReadingsChart(state.readings, filterNotesByPeriod(state.allNotes, state.period))
            }

            HorizontalDivider()

            ReadingsTable(
                readings = state.readings,
                onRowClick = { editingReading = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

/**
 * Period-average summary for the readings currently shown on the chart, with the average's
 * AHA category so the user gets an at-a-glance verdict, not just numbers.
 */
@Composable
private fun PeriodStatsRow(readings: List<Reading>) {
    if (readings.isEmpty()) return
    val avgSys = readings.map { it.systolicMmHg }.average().roundToInt()
    val avgDia = readings.map { it.diastolicMmHg }.average().roundToInt()
    val avgHr = readings.map { it.heartRateBpm }.average().roundToInt()
    val category = BpCategory.of(avgSys, avgDia)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Average", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$avgSys/$avgDia",
                    style = MaterialTheme.typography.titleMedium,
                    color = categoryColor(category)
                )
                Text(
                    "  mmHg · $avgHr bpm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            category.label,
            style = MaterialTheme.typography.labelLarge,
            color = categoryColor(category)
        )
    }
}

@Composable
private fun SyncButton(state: HistoryUiState, onClick: () -> Unit) {
    if (state.syncStatus == SyncStatus.SYNCING) {
        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
    } else {
        TextButton(onClick = onClick) {
            Text(if (state.backupFolderName == null) "Set backup" else "Sync now")
        }
    }
}

@Composable
private fun SyncStatusLine(state: HistoryUiState) {
    val readingWord = if (state.totalReadingsCount == 1) "reading" else "readings"
    val text = when {
        state.syncStatus == SyncStatus.ERROR && state.syncError != null ->
            "Sync failed: ${state.syncError}"
        state.backupFolderName == null ->
            "No backup folder chosen yet · ${state.totalReadingsCount} $readingWord saved locally"
        state.lastSyncedAt != null -> {
            val staleNote = if (state.isBackupStale) " · tap Sync now, it's been a while" else ""
            "Backed up to \"${state.backupFolderName}\" · last synced ${Formatters.dateTime(state.lastSyncedAt)}$staleNote"
        }
        else -> "Backup folder: \"${state.backupFolderName}\" · not yet synced"
    }
    val color = when {
        state.syncStatus == SyncStatus.ERROR -> MaterialTheme.colorScheme.error
        state.isBackupStale -> StatusElevated
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}
