package com.robcloud.bloodpressure.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.robcloud.bloodpressure.data.Arm
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.ui.showDatePicker
import com.robcloud.bloodpressure.ui.showTimePicker
import com.robcloud.bloodpressure.ui.validateReading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReadingDialog(
    reading: Reading,
    onDismiss: () -> Unit,
    onSave: (Reading) -> Unit,
    onDelete: (Reading) -> Unit
) {
    val context = LocalContext.current
    var systolic by remember { mutableStateOf(reading.systolicMmHg.toString()) }
    var diastolic by remember { mutableStateOf(reading.diastolicMmHg.toString()) }
    var heartRate by remember { mutableStateOf(reading.heartRateBpm.toString()) }
    var arm by remember { mutableStateOf(reading.arm) }
    var takenAt by remember { mutableStateOf(reading.takenAt) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete reading?") },
            text = { Text("This removes the reading from the app and from the backup file on the next sync.") },
            confirmButton = {
                TextButton(onClick = { onDelete(reading) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Edit reading", style = MaterialTheme.typography.titleLarge)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = systolic,
                        onValueChange = { systolic = it.filter(Char::isDigit).take(3); errorMessage = null },
                        label = { Text("Systolic") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = diastolic,
                        onValueChange = { diastolic = it.filter(Char::isDigit).take(3); errorMessage = null },
                        label = { Text("Diastolic") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = heartRate,
                    onValueChange = { heartRate = it.filter(Char::isDigit).take(3); errorMessage = null },
                    label = { Text("Heart rate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Arm.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = arm == entry,
                            onClick = { arm = entry },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Arm.entries.size)
                        ) {
                            Text(if (entry == Arm.LEFT) "Left" else "Right")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showDatePicker(context, takenAt) { takenAt = it } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(Formatters.date(takenAt))
                    }
                    OutlinedButton(
                        onClick = { showTimePicker(context, takenAt) { takenAt = it } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(Formatters.time(takenAt))
                    }
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(onClick = {
                            val sys = systolic.toIntOrNull()
                            val dia = diastolic.toIntOrNull()
                            val hr = heartRate.toIntOrNull()
                            val error = validateReading(sys, dia, hr)
                            if (error != null) {
                                errorMessage = error
                            } else {
                                onSave(
                                    reading.copy(
                                        systolicMmHg = sys!!,
                                        diastolicMmHg = dia!!,
                                        heartRateBpm = hr!!,
                                        arm = arm,
                                        takenAt = takenAt
                                    )
                                )
                            }
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
