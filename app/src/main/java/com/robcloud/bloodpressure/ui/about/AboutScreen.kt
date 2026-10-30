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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robcloud.bloodpressure.BloodPressureApp
import com.robcloud.bloodpressure.BuildConfig
import com.robcloud.bloodpressure.backup.BackupStatus
import com.robcloud.bloodpressure.backup.status
import com.robcloud.bloodpressure.ocr.VisionServerProfile
import com.robcloud.bloodpressure.ocr.VisionServerStore
import com.robcloud.bloodpressure.ui.EqualWidthSegmentedRow
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.update.UpdateCheckFrequency
import com.robcloud.bloodpressure.update.UpdateConfig
import com.robcloud.bloodpressure.update.UpdateManager
import com.robcloud.bloodpressure.update.UpdatePrefsStore
import com.robcloud.bloodpressure.update.UpdateScheduler
import com.robcloud.bloodpressure.update.UpdateUiState
import com.robcloud.bloodpressure.update.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

private const val FEEDBACK_EMAIL = "android.bp@robcloud.qzz.io"

/** Newest first; keep the three most recent versions here (older entries drop off). */
private val CHANGELOG = listOf(
    "2.11.2" to listOf(
        "Test release: no changes to the app itself. Published to check the release pipeline end to end.",
    ),
    "2.11.1" to listOf(
        "Deleting a saved scan server now asks first, since it can't be undone — and warns you if it's the active one, since another saved server (possibly a cloud one) would silently take over.",
        "Added a warning when a saved server's address isn't encrypted (http, not https) and has an API key set — that key would otherwise be sent in plain text.",
        "An existing single scan-server setup from before this version is cleared from disk once it's carried over, instead of being left there indefinitely.",
        "Fixed: a corrupted saved server could make every other saved server disappear the next time any of them changed.",
        "Fixed: the \"Add a server\" dialog could lose everything typed into it if the app was interrupted while you were away copying an API key.",
        "Various other reliability fixes under the hood.",
    ),
    "2.11.0" to listOf(
        "New: save more than one reading-scan server and switch between them — e.g. a self-hosted model at home and a cloud provider's free tier for when you're out. Set up from the About tab's \"Reading scan\" card.",
        "An existing single scan-server setup carries over automatically as your first saved server.",
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
            ProjectLink()
        }

        BackupSection()

        FeedbackButton()

        UpdateSection(updateViewModel)

        ScanServerSection()

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

/** Opens the project's GitHub page (source, releases and issues) in the browser. */
@Composable
private fun ProjectLink() {
    val url = UpdateConfig.projectUrl
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    )
    Text(
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(url, linkStyles)) { append(url.removePrefix("https://")) }
        },
        style = MaterialTheme.typography.bodyMedium
    )
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

/**
 * Where the Add reading tab's "Scan reading" photo import sends the photo — any number of saved
 * servers (see [VisionServerStore]), self-hosted or a cloud provider's free tier, switched between
 * freely. Reads the store directly for the same reason [BackupSection] does: About has no
 * ViewModel of its own and is recomposed fresh on every visit, so a plain read always reflects the
 * current setting.
 */
@Composable
private fun ScanServerSection() {
    val context = LocalContext.current
    val store = remember { VisionServerStore(context) }
    var profiles by remember { mutableStateOf(store.profiles()) }
    // activeProfile(), not the raw activeProfileId: a stale selection (pointing at an id no
    // longer in the list) self-heals there, and reading it this way — not just when a scan runs
    // — is what makes that correction show up here instead of leaving every radio unselected.
    var activeId by remember { mutableStateOf(store.activeProfile()?.id) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VisionServerProfile?>(null) }

    fun refresh() {
        profiles = store.profiles()
        activeId = store.activeProfile()?.id
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Reading scan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Works with any server that speaks the OpenAI chat-completions API with image " +
                    "input: a self-hosted model (Ollama, LM Studio, llama.cpp's server) keeps the " +
                    "photo on your own network; a cloud provider's free tier (Google, OpenRouter, " +
                    "...) sends it to them instead. Save as many as you like and pick which is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (profiles.isEmpty()) {
                Text(
                    "Not set up yet — the Add reading tab's Scan button needs at least one server.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // selectableGroup(): with the per-row selectable() below (not a plain clickable),
                // this is what lets TalkBack announce the set as "radio button, N of M" instead
                // of N separate unlabelled stops — see ScanServerRow.
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.selectableGroup()
                ) {
                    profiles.forEach { profile ->
                        ScanServerRow(
                            profile = profile,
                            selected = profile.id == activeId,
                            onSelect = { store.activeProfileId = profile.id; refresh() },
                            onEdit = { editingId = profile.id },
                            onDelete = { deleting = profile }
                        )
                    }
                }
            }
            OutlinedButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Add a server")
            }
        }
    }

    if (adding) {
        ScanServerDialog(
            initial = null,
            onSave = store::upsertProfile,
            onDismiss = { adding = false; refresh() }
        )
    }
    // Looked up by id each recomposition, not held as the profile itself: a data class isn't
    // rememberSaveable without a custom Saver, and the id is all that needs to survive a process
    // death or rotation while the dialog is open (see ScanServerDialog for why that matters here).
    profiles.find { it.id == editingId }?.let { profile ->
        ScanServerDialog(
            initial = profile,
            onSave = store::upsertProfile,
            onDismiss = { editingId = null; refresh() }
        )
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${profile.name}\"?") },
            text = {
                Text(
                    if (profile.id == activeId) {
                        "It's the active server — scanning will switch to another saved one, " +
                            "or stop being available if this was the last one."
                    } else {
                        "This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { store.deleteProfile(profile.id); deleting = null; refresh() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ScanServerRow(
    profile: VisionServerProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // onClick = null: the Row above is the single focus/click target (selectable with a
        // RadioButton role), so TalkBack gets one stop per server instead of two. Matches
        // IrregularHeartbeatCheckbox's Checkbox(onCheckedChange = null) for the same reason.
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${profile.model} · ${profile.baseUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete ${profile.name}")
        }
    }
}

/**
 * [initial] null adds a new profile; non-null edits it in place (same id, pre-filled fields).
 * Fields are rememberSaveable: this is the dialog for the exact flow that's likeliest to get
 * interrupted — switching away to a browser to copy an API key — so losing what was typed to a
 * rotation or the system reclaiming the process while away would be a bad way to lose that, same
 * reasoning as ExportCsvDialog's fields and HistoryScreen/MainActivity's open-dialog flags.
 */
@Composable
private fun ScanServerDialog(initial: VisionServerProfile?, onSave: (VisionServerProfile) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var baseUrl by rememberSaveable { mutableStateOf(initial?.baseUrl ?: "") }
    var model by rememberSaveable { mutableStateOf(initial?.model ?: "") }
    var apiKey by rememberSaveable { mutableStateOf(initial?.apiKey ?: "") }
    // Checked against the same normalisation Save applies (trim + drop a trailing slash), so a
    // URL that's blank once normalised (e.g. just "/") is rejected here instead of saving as "" —
    // which would otherwise pass this check, then make the Scan button appear on Add reading only
    // for every scan to fail with a raw "no protocol" error.
    val normalizedUrl = baseUrl.trim().trimEnd('/')
    val canSave = name.isNotBlank() && normalizedUrl.isNotBlank() && model.isNotBlank()
    // A plain http:// address isn't encrypted — fine for a self-hosted server on a network you
    // trust, but an API key sent to one would cross the network in the clear. Only worth flagging
    // once there's a key to protect; a local server with no key has nothing this warning is about.
    val cleartextKeyWarning = apiKey.isNotBlank() && normalizedUrl.startsWith("http://", ignoreCase = true)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (initial == null) "Add a scan server" else "Edit scan server",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "An OpenAI-compatible server with a vision model — self-hosted (e.g. Ollama " +
                        "running qwen2.5vl or llama3.2-vision) or a cloud provider's free tier " +
                        "(e.g. Google Gemini, OpenRouter).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Gemini Flash (free)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.50:11434") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model name") },
                    placeholder = { Text("qwen2.5vl") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (cleartextKeyWarning) {
                    Text(
                        "This address isn't encrypted (http, not https) — the API key would be " +
                            "sent in plain text. Only use this on a network you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                VisionServerProfile(
                                    id = initial?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    baseUrl = normalizedUrl,
                                    model = model.trim(),
                                    apiKey = apiKey.trim().ifBlank { null }
                                )
                            )
                            onDismiss()
                        }
                    ) { Text("Save") }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    // Locale.US, not the device default: a size like "1.5 GB" shouldn't become "1,5 GB" on a
    // device set to a comma-decimal locale.
    return when {
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
