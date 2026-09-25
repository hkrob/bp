package com.robcloud.bloodpressure.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.BuildConfig
import com.robcloud.bloodpressure.backup.BackupStatus
import com.robcloud.bloodpressure.backup.status
import com.robcloud.bloodpressure.ui.EqualWidthSegmentedRow
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.update.UpdateCheckFrequency
import com.robcloud.bloodpressure.update.UpdateManager
import com.robcloud.bloodpressure.update.UpdatePrefsStore
import com.robcloud.bloodpressure.update.UpdateScheduler
import com.robcloud.bloodpressure.update.UpdateUiState
import com.robcloud.bloodpressure.update.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

private const val FEEDBACK_EMAIL = "android.bp@robcloud.qzz.io"

/** Newest first; keep the three most recent versions here (older entries drop off). */
private val CHANGELOG = listOf(
    "2.7.0" to listOf(
        "Low readings (under 90 systolic or under 60 diastolic) are now marked \"Low\", in blue.",
        "Readings taken within 10 minutes of each other are grouped as a sitting, with the sitting's average shown in History and counted in the PDF report.",
        "History and the PDF report show separate averages for mornings (before 12:00) and afternoons & evenings.",
        "You can record that the monitor showed an irregular heartbeat. Flagged readings are marked IRR in History, the Log and the PDF report, and the flag is kept in the backup file.",
        "Daily reminders follow the phone's time zone: after travelling, or changing the clock, they come at the chosen local time straight away instead of a day later.",
        "Deleting a reading or note can now be undone from the message that confirms it.",
        "A reading or note you are part way through entering is kept if Android closes the app in the background.",
        "The update check now says when GitHub's hourly limit has been reached, and how long to wait, instead of \"Couldn't reach GitHub\".",
        "The backup file gains an irregular_heartbeat column. Older versions of the app will refuse to sync with it (nothing is lost), so update the app on every phone that uses the same backup folder."
    ),
    "2.6.1" to listOf(
        "Confirmation messages are back: \"Reading saved\", \"Note saved\", \"Reading deleted\", and the results of Import and Export (including any errors) were being cleared before they could appear in 2.6.0."
    ),
    "2.6.0" to listOf(
        "Safer backups: a sync can no longer undo an edit or bring back a deleted reading, rows the app can't read are never overwritten, a readings.csv written by another app is left alone, and a dated copy of the backup file is kept each day.",
        "You can now change the backup folder, and after moving to a new phone the app asks you to re-link it instead of failing silently. Backups that keep failing now show a warning on Add reading.",
        "Add reading remembers which arm you use, keeps a date or time you picked when you switch apps, won't save a reading twice on a double tap, and won't accept a time in the future.",
        "Medication Taken notes added from the Add note tab record the real time, and the time can be corrected when editing.",
        "Daily reminders stay at the time you set (they used to drift later and shift by an hour at daylight-saving changes), and the app tells you when notifications are blocked.",
        "Import only adds readings and notes that are missing, so it no longer reverts later edits, and large files now import on Android 8-11.",
        "History scrolls as one page, so the readings list no longer vanishes in landscape, and older readings show their year.",
        "Fixes to the PDF report (note markers, medication times, long notes), Log column alignment, 24-hour time pickers and status bar icons in Dark and Console themes."
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

        BackupSection()

        FeedbackButton()

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
                "History chart, browse a dense text log, back up automatically after every " +
                "change (with a dated copy kept each day), and set daily reminders to keep " +
                "your readings up to date.",
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

/**
 * Where readings/notes are mirrored to, if anywhere, and whether that is working. Reads
 * [com.robcloud.bloodpressure.backup.BackupFolderStore] directly — About is a plain info screen
 * with no ViewModel of its own, and this tab is torn down and recomposed fresh on every visit (see
 * `MainActivity`'s tab switcher), so loading once per visit always reflects the current state.
 * Loaded on the IO dispatcher: resolving the folder name is a storage-provider call.
 */
@Composable
private fun BackupSection() {
    val context = LocalContext.current
    val store = remember { (context.applicationContext as BloodPressureApp).backupFolderStore }
    val status by produceState<BackupStatus?>(initialValue = null, store) {
        value = withContext(Dispatchers.IO) {
            store.displayName()
            store.status()
        }
    }
    val backup = status ?: return
    val folderName = backup.folderName
    val stale = backup.lastSyncedAt == null ||
        backup.lastSyncedAt.isBefore(Instant.now().minus(Duration.ofDays(7)))
    val failing = backup.lastError != null
    val atRisk = folderName == null || backup.needsRelink || failing || backup.localOnly || stale
    // "Documents" alone doesn't say whether that's a cloud folder or plain device storage —
    // spell out the provider when we recognise it (Drive, Dropbox, ...), or fall back to a
    // generic "cloud storage" so the destination is never ambiguous.
    val destination = when {
        folderName == null -> null
        backup.localOnly -> "\"$folderName\" (this device's local storage)"
        backup.providerLabel != null -> "\"$folderName\" on ${backup.providerLabel}"
        else -> "\"$folderName\" (cloud storage)"
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    destination == null ->
                        "Not backed up — no folder set. Choose one from the History tab."
                    backup.needsRelink ->
                        "Lost access to $destination (for example after moving to a new phone). Re-link it from the History tab."
                    failing ->
                        "Backups to $destination are failing: ${backup.lastError}"
                    backup.localOnly ->
                        "Backed up to $destination — won't survive a lost or wiped phone."
                    backup.lastSyncedAt != null ->
                        "Backed up to $destination · last synced ${Formatters.dateTime(backup.lastSyncedAt)}"
                    else ->
                        "Backup folder: $destination · not yet synced"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (atRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (destination != null) {
                Text(
                    "Each day the previous backup file is also kept in a \"snapshots\" folder beside it " +
                        "(every day for two weeks, then one a month for a year). To go back to one, use " +
                        "Import CSV on the History tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeedbackButton() {
    val context = LocalContext.current
    OutlinedButton(onClick = { sendFeedbackEmail(context) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Send feedback")
    }
}

/**
 * Opens an email draft addressed to [FEEDBACK_EMAIL], with the app version and device details
 * pre-filled below a blank line so the user can just start typing at the top.
 */
private fun sendFeedbackEmail(context: Context) {
    val body = "\n\n" +
        "—\n" +
        "Please leave the details below — they help track down the issue:\n" +
        "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
        "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "BP Tracker feedback (v${BuildConfig.VERSION_NAME})")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
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
            EqualWidthSegmentedRow(
                options = UpdateCheckFrequency.entries,
                selected = frequency,
                label = { it.label },
                onSelect = { f ->
                    frequency = f
                    store.frequency = f
                    UpdateScheduler.schedule(context, f)
                }
            )

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
