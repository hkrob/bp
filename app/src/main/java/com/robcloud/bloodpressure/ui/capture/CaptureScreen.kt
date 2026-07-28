package com.robcloud.bloodpressure.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.robcloud.bloodpressure.update.UpdateViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robcloud.bloodpressure.data.Arm
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.data.bpCategory
import com.robcloud.bloodpressure.ui.DIASTOLIC_MAX
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.ui.SYSTOLIC_MAX
import com.robcloud.bloodpressure.ui.isFieldComplete
import com.robcloud.bloodpressure.ui.history.categoryColor
import com.robcloud.bloodpressure.ui.showDatePicker
import com.robcloud.bloodpressure.ui.showTimePicker
import com.robcloud.bloodpressure.ui.theme.StatusHigh
import com.robcloud.bloodpressure.ui.theme.StatusNormal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val lastReading by viewModel.lastReading.collectAsState()
    val previousReading by viewModel.previousReading.collectAsState()
    val cachedRelease by updateViewModel.cachedRelease.collectAsState()
    var updateBannerDismissed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val diastolicFocus = remember { FocusRequester() }
    val heartRateFocus = remember { FocusRequester() }

    LaunchedEffect(state.justSaved) {
        if (state.justSaved) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar("Reading saved")
            viewModel.consumeSavedFlag()
        }
    }

    LaunchedEffect(state.medicationSaved) {
        if (state.medicationSaved) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar("Medication taken noted")
            viewModel.consumeMedicationSavedFlag()
        }
    }

    state.crisisBp?.let { bp ->
        CrisisWarningDialog(bp = bp, onDismiss = viewModel::consumeCrisisWarning)
    }

    // Stamp the form with the current date & time whenever this screen comes to the foreground —
    // on app launch/resume and on returning to this tab (adding the observer while already RESUMED
    // replays ON_RESUME). The date/time pickers are plain Dialogs, which don't change the activity
    // lifecycle, so a manually chosen date is never overwritten while the user stays on the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.setTakenAtNow()
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
            Text(
                text = "New reading",
                style = MaterialTheme.typography.headlineMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = state.systolic,
                    onValueChange = { value ->
                        viewModel.updateSystolic(value)
                        if (isFieldComplete(value, SYSTOLIC_MAX)) diastolicFocus.requestFocus()
                    },
                    label = { Text("Systolic") },
                    suffix = { Text("mmHg") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.diastolic,
                    onValueChange = { value ->
                        viewModel.updateDiastolic(value)
                        if (isFieldComplete(value, DIASTOLIC_MAX)) heartRateFocus.requestFocus()
                    },
                    label = { Text("Diastolic") },
                    suffix = { Text("mmHg") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(diastolicFocus),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = state.heartRate,
                onValueChange = viewModel::updateHeartRate,
                label = { Text("Heart rate") },
                suffix = { Text("bpm") },
                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(heartRateFocus),
                singleLine = true
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Arm.entries.forEachIndexed { index, arm ->
                    SegmentedButton(
                        selected = state.arm == arm,
                        onClick = { viewModel.updateArm(arm) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Arm.entries.size)
                    ) {
                        Text(if (arm == Arm.LEFT) "Left arm" else "Right arm")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker(context, state.takenAt, viewModel::updateTakenAt) },
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(Formatters.date(state.takenAt), maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showTimePicker(context, state.takenAt, viewModel::updateTakenAt) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(Formatters.time(state.takenAt), maxLines = 1)
                }
                OutlinedButton(
                    onClick = viewModel::setTakenAtNow,
                    modifier = Modifier.weight(0.7f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Now", maxLines = 1)
                }
            }

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Save reading")
            }

            OutlinedButton(
                onClick = viewModel::saveMedicationTaken,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Medication, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Medication taken")
            }

            // Update banner — only visible when a newer version was found and not yet dismissed.
            if (cachedRelease != null && !updateBannerDismissed) {
                UpdateAvailableBanner(
                    versionName = cachedRelease!!.versionName,
                    onDismiss = { updateBannerDismissed = true }
                )
            }

            // Reference only — kept below the entry fields so capturing a reading never needs a scroll.
            lastReading?.let { LastReadingCard(it, previousReading) }
        }

        SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(data.visuals.message) }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(versionName: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Update available: v$versionName — see About tab",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun LastReadingCard(reading: Reading, previous: Reading?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Last reading",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    reading.bpCategory().label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = categoryColor(reading.bpCategory())
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${reading.systolicMmHg}/${reading.diastolicMmHg}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "mmHg  ·  ${reading.heartRateBpm} bpm  ·  ${Formatters.dateTime(reading.takenAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            previous?.let { TrendRow(reading, it) }
        }
    }
}

@Composable
private fun TrendRow(latest: Reading, previous: Reading) {
    val sysDelta = latest.systolicMmHg - previous.systolicMmHg
    val diaDelta = latest.diastolicMmHg - previous.diastolicMmHg
    val (icon, tint) = when {
        sysDelta > 0 -> Icons.Filled.ArrowUpward to StatusHigh
        sysDelta < 0 -> Icons.Filled.ArrowDownward to StatusNormal
        else -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            "${signed(sysDelta)}/${signed(diaDelta)} mmHg vs last reading",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

/**
 * Advisory shown after saving a reading in the hypertensive-crisis range (≥180 systolic or
 * ≥120 diastolic, per AHA). The reading is already saved — this never blocks data entry.
 * Custom Dialog+Surface rather than AlertDialog: see ReminderSettingsDialog for the
 * touch-unresponsive AlertDialog buttons this app hit on this device.
 */
@Composable
private fun CrisisWarningDialog(bp: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Very high reading",
                    style = MaterialTheme.typography.titleLarge,
                    color = StatusHigh
                )
                Text(
                    "$bp mmHg is in the hypertensive crisis range (180+ systolic or " +
                        "120+ diastolic). Rest for a few minutes and take a second reading. " +
                        "If it stays this high, or you have symptoms such as chest pain, " +
                        "shortness of breath, numbness, or vision changes, seek medical " +
                        "attention right away.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "The reading has been saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
        }
    }
}

