package com.robcloud.bloodpressure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.robcloud.bloodpressure.reminders.ReminderSettings
import com.robcloud.bloodpressure.reminders.ReminderTime

@Composable
fun ReminderSettingsDialog(
    settings: ReminderSettings,
    onDismiss: () -> Unit,
    onSave: (ReminderSettings) -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(settings.enabled) }
    val times = remember { mutableStateListOf(*settings.times.toTypedArray()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Daily reminders", style = MaterialTheme.typography.titleLarge)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Remind me every day")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (enabled) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(times, key = { it.id }) { time ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(onClick = {
                                    showTimePickerFor(context, time.hour, time.minute) { h, m ->
                                        val index = times.indexOfFirst { it.id == time.id }
                                        if (index >= 0) times[index] = time.copy(hour = h, minute = m)
                                    }
                                }) {
                                    Text("%02d:%02d".format(time.hour, time.minute))
                                }
                                IconButton(
                                    onClick = { times.removeAll { it.id == time.id } },
                                    enabled = times.size > 1
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove reminder")
                                }
                            }
                        }
                    }
                    TextButton(onClick = { times.add(ReminderTime(hour = 9, minute = 0)) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Add reminder")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onSave(ReminderSettings(enabled, times.toList())) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
