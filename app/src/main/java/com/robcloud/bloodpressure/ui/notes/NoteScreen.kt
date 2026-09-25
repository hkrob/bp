package com.robcloud.bloodpressure.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robcloud.bloodpressure.data.NOTE_DETAILS_MAX_LENGTH
import com.robcloud.bloodpressure.data.NoteType
import com.robcloud.bloodpressure.ui.showDatePickerFor
import com.robcloud.bloodpressure.ui.showTimePickerFor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val noteDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val noteTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(viewModel: NoteViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    // Unit-keyed collector, as in CaptureScreen: consuming the flag mustn't cancel the snackbar.
    LaunchedEffect(Unit) {
        viewModel.uiState.map { it.justSaved }.distinctUntilChanged().filter { it }.collect {
            viewModel.consumeSavedFlag()
            snackbarHostState.showSnackbar("Note saved")
        }
    }

    // Follow the calendar/clock until the user picks a date or time (see NoteViewModel).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(text = "Add note", style = MaterialTheme.typography.headlineMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePickerFor(context, state.date, viewModel::updateDate) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(noteDateFormatter.format(state.date), maxLines = 1)
                }
                if (state.noteType == NoteType.MEDICATION_TAKEN) {
                    OutlinedButton(
                        onClick = {
                            showTimePickerFor(context, state.time.hour, state.time.minute) { h, m ->
                                viewModel.updateTime(LocalTime.of(h, m))
                            }
                        },
                        modifier = Modifier.weight(0.8f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(noteTimeFormatter.format(state.time), maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = viewModel::setDateToday,
                    modifier = Modifier.weight(0.7f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Today", maxLines = 1)
                }
            }

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = state.noteType.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Note type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false }
                ) {
                    NoteType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                viewModel.updateNoteType(type)
                                typeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.noteType.abbreviation,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Abbreviation") },
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = state.details,
                    onValueChange = viewModel::updateDetails,
                    label = { Text("Details") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${state.details.length} / $NOTE_DETAILS_MAX_LENGTH",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            state.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Save note")
            }
        }

        SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(data.visuals.message) }
        }
    }
}
