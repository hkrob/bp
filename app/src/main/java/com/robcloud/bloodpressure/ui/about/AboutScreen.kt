package com.robcloud.bloodpressure.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.robcloud.bloodpressure.BuildConfig
import com.robcloud.bloodpressure.update.UpdateCheckFrequency
import com.robcloud.bloodpressure.update.UpdateManager
import com.robcloud.bloodpressure.update.UpdatePrefsStore
import com.robcloud.bloodpressure.update.UpdateScheduler
import com.robcloud.bloodpressure.update.UpdateUiState
import com.robcloud.bloodpressure.update.UpdateViewModel

/** Newest first; keep the three most recent versions here (older entries drop off). */
private val CHANGELOG = listOf(
    "2.5.1" to listOf(
        "Fixed: segmented buttons in the Updates section now have equal width.",
        "Show download file size in update states so you know what you're downloading."
    ),
    "2.5" to listOf(
        "Update banner on Add Reading tab: a notification appears above Last Reading when a newer version is available.",
        "Configurable update-check frequency: choose Never, Daily, Weekly, or Monthly in the About tab."
    ),
    "2.4" to listOf(
        "Faster entry: typing the diastolic value now jumps straight to heart rate.",
        "The date and time reset to now every time the app is opened.",
        "\"Last reading\" moved below the entry fields, so a new reading fits without scrolling."
    ),
    "2.3" to listOf(
        "Log tab: notes and readings now share the same compact, time-stamped layout.",
        "Medication Taken notes record the exact time and appear in the correct order among readings.",
        "Other notes are timestamped at 00:01 so they sort consistently at the start of their day."
    ),
)

@Composable
fun AboutScreen(updateViewModel: UpdateViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("About", style = MaterialTheme.typography.headlineMedium)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("BP Tracker", style = MaterialTheme.typography.titleLarge)
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Built ${BuildConfig.BUILD_DATE}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UpdateSection(updateViewModel)

        Text(
            "A personal blood pressure tracker. Readings and notes are stored on this " +
                "device and can be mirrored as a plain CSV file to a folder you choose — " +
                "commonly a Google Drive folder — so your history is easy to read, export, " +
                "or move between devices without any account sign-in inside the app.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            "Capture readings and notes, review trends and dates side by side on the " +
                "History chart, browse a dense text log, back up automatically once a day, " +
                "and set daily reminders to keep your readings up to date.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("What's new", style = MaterialTheme.typography.titleLarge)

        CHANGELOG.forEach { (version, changes) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Version $version", style = MaterialTheme.typography.titleMedium)
                changes.forEach { change ->
                    Text(
                        "•  $change",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSection(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val store = remember { UpdatePrefsStore(context) }
    var frequency by remember { mutableStateOf(store.frequency) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Updates", style = MaterialTheme.typography.titleMedium)

            // Frequency picker
            val frequencies = UpdateCheckFrequency.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                frequencies.forEachIndexed { index, f ->
                    SegmentedButton(
                        selected = frequency == f,
                        onClick = {
                            frequency = f
                            store.frequency = f
                            UpdateScheduler.schedule(context, f)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = frequencies.size),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Text(f.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            when (val s = state) {
                is UpdateUiState.Idle -> {
                    Text(
                        "Check GitHub for a newer version.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = viewModel::check) { Text("Check for updates") }
                }

                is UpdateUiState.Checking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    Text("Checking…", style = MaterialTheme.typography.bodyMedium)
                }

                is UpdateUiState.UpToDate -> {
                    Text(
                        "You're on the latest version (${BuildConfig.VERSION_NAME}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = viewModel::check) { Text("Check again") }
                }

                is UpdateUiState.Available -> {
                    Text(
                        "Version ${s.release.versionName} is available " +
                            "(you have ${BuildConfig.VERSION_NAME}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (s.release.notes.isNotBlank()) {
                        Text(
                            s.release.notes.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val sizeText = if (s.release.apkSizeBytes > 0) " (${formatBytes(s.release.apkSizeBytes)})" else ""
                    Button(onClick = { viewModel.download(s.release) }) {
                        Text("Download & install$sizeText")
                    }
                }

                is UpdateUiState.Downloading -> {
                    val sizeText = if (s.sizeBytes > 0) " (${formatBytes(s.sizeBytes)})" else ""
                    Text("Downloading… ${s.progress}%$sizeText", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { s.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is UpdateUiState.ReadyToInstall -> {
                    val sizeText = if (s.sizeBytes > 0) " (${formatBytes(s.sizeBytes)})" else ""
                    Text(
                        "Downloaded version ${s.versionName}$sizeText.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = {
                        if (UpdateManager.canInstall(context)) {
                            UpdateManager.installApk(context, s.file)
                        } else {
                            UpdateManager.openInstallPermissionSettings(context)
                        }
                    }) { Text("Install version ${s.versionName}") }
                    Text(
                        "If prompted, allow BP Tracker to install apps, then tap Install again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is UpdateUiState.Error -> {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = viewModel::check) { Text("Try again") }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
