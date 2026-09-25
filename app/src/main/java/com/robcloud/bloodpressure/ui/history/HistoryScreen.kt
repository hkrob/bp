package com.robcloud.bloodpressure.ui.history

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.ui.EqualWidthSegmentedRow
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.ui.theme.StatusElevated
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val pendingReportShare by viewModel.pendingReportShare.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editingReading by remember { mutableStateOf<Reading?>(null) }
    var exportDialogOpen by rememberSaveable { mutableStateOf(false) }

    // Registered through Compose rather than bridged via the Activity, so a result that arrives
    // after the Activity was recreated (rotation, dark-mode switch) still reaches the ViewModel.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importCsv)
    }
    val backupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::setBackupFolder)
    }
    val pickBackupFolder = { backupFolderLauncher.launch(null) }

    // Consume before showing: the snackbar suspends until dismissed, and leaving the tab
    // meanwhile would otherwise replay the message on the next visit. Collected in one
    // Unit-keyed effect — an effect keyed on the message would be cancelled (snackbar and
    // all) by the very recomposition that consuming it triggers.
    LaunchedEffect(Unit) {
        viewModel.message.filterNotNull().collect { text ->
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(text)
        }
    }

    LaunchedEffect(pendingReportShare) {
        pendingReportShare?.let { uri ->
            viewModel.consumeReportShare()
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Blood Pressure Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Share report"))
        }
    }

    if (exportDialogOpen) {
        ExportCsvDialog(
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
        // One scrolling list, summary first: with a fixed summary above the table, landscape or
        // a large font left the table no height at all.
        ReadingsTable(
            readings = state.readings,
            onRowClick = { editingReading = it },
            modifier = Modifier.fillMaxSize(),
            header = {
                Column {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("History", style = MaterialTheme.typography.headlineMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
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
                                        if (!state.backup.configured || state.backup.needsRelink) {
                                            pickBackupFolder()
                                        } else {
                                            viewModel.syncNow()
                                        }
                                    }
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SyncStatusLine(state, Modifier.weight(1f))
                            if (state.backup.configured && !state.backup.needsRelink && !state.syncing) {
                                TextButton(onClick = pickBackupFolder) { Text("Change") }
                            }
                        }

                        EqualWidthSegmentedRow(
                            options = Period.entries,
                            selected = state.period,
                            label = { it.label },
                            onSelect = viewModel::selectPeriod
                        )

                        PeriodStatsRow(state.readings)

                        ReadingsChart(state.readings)
                    }
                    HorizontalDivider()
                }
            }
        )

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
    if (state.syncing) {
        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
    } else {
        TextButton(onClick = onClick) {
            Text(
                when {
                    !state.backup.configured -> "Set backup"
                    state.backup.needsRelink -> "Re-link"
                    else -> "Sync now"
                }
            )
        }
    }
}

@Composable
private fun SyncStatusLine(state: HistoryUiState, modifier: Modifier = Modifier) {
    val backup = state.backup
    val readingWord = if (state.totalReadingsCount == 1) "reading" else "readings"
    val text = when {
        !backup.configured ->
            "No backup folder chosen yet · ${state.totalReadingsCount} $readingWord saved locally"
        backup.needsRelink ->
            "Lost access to \"${backup.folderName}\" (for example after moving to a new phone) · tap Re-link and choose it again"
        backup.lastError != null && !state.syncing -> {
            val lastGood = backup.lastSyncedAt?.let { " · last good sync ${Formatters.dateTime(it)}" }.orEmpty()
            "Sync failed: ${backup.lastError}$lastGood"
        }
        backup.lastSyncedAt != null -> {
            val staleNote = if (state.isBackupStale) " · tap Sync now, it's been a while" else ""
            "Backed up to \"${backup.folderName}\" · last synced ${Formatters.dateTime(backup.lastSyncedAt)}$staleNote"
        }
        else -> "Backup folder: \"${backup.folderName}\" · not yet synced"
    }
    val color = when {
        backup.needsRelink || (backup.lastError != null && !state.syncing) -> MaterialTheme.colorScheme.error
        state.isBackupStale -> StatusElevated
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}
