package com.robcloud.bloodpressure

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.robcloud.bloodpressure.backup.StorageHost
import com.robcloud.bloodpressure.reminders.NotificationPermissionHost
import com.robcloud.bloodpressure.reminders.ReminderScheduler
import com.robcloud.bloodpressure.reminders.ReminderSettings
import com.robcloud.bloodpressure.reminders.ReminderStore
import com.robcloud.bloodpressure.ui.ReminderSettingsDialog
import com.robcloud.bloodpressure.ui.about.AboutScreen
import com.robcloud.bloodpressure.ui.capture.CaptureScreen
import com.robcloud.bloodpressure.ui.history.HistoryScreen
import com.robcloud.bloodpressure.ui.history.LogScreen
import com.robcloud.bloodpressure.ui.notes.NoteScreen
import com.robcloud.bloodpressure.ui.theme.BloodPressureTheme
import com.robcloud.bloodpressure.ui.theme.ThemeMode
import com.robcloud.bloodpressure.ui.theme.ThemeStore
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity(), StorageHost, NotificationPermissionHost {

    private var pendingFolderContinuation: CancellableContinuation<Uri?>? = null
    private var pendingOpenContinuation: CancellableContinuation<Uri?>? = null
    private var pendingPermissionContinuation: CancellableContinuation<Boolean>? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingPermissionContinuation?.resume(granted)
        pendingPermissionContinuation = null
    }

    override suspend fun requestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return suspendCancellableCoroutine { continuation ->
            pendingPermissionContinuation = continuation
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        pendingFolderContinuation?.resume(uri)
        pendingFolderContinuation = null
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingOpenContinuation?.resume(uri)
        pendingOpenContinuation = null
    }

    override suspend fun pickFolder(): Uri? =
        suspendCancellableCoroutine { continuation ->
            pendingFolderContinuation = continuation
            folderPickerLauncher.launch(null)
        }

    override suspend fun openDocument(): Uri? =
        suspendCancellableCoroutine { continuation ->
            pendingOpenContinuation = continuation
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themeStore = ThemeStore(this)
        setContent {
            var themeMode by remember { mutableStateOf(themeStore.get()) }
            BloodPressureTheme(themeMode) {
                BpTrackerApp(
                    storageHost = this,
                    notificationPermissionHost = this,
                    themeMode = themeMode,
                    onThemeChange = { mode ->
                        themeMode = mode
                        themeStore.set(mode)
                    }
                )
            }
        }
    }
}

private enum class AppTab(val title: String) {
    CAPTURE("Add reading"),
    NOTE("Add note"),
    HISTORY("History"),
    LOG("Log"),
    ABOUT("About")
}

@Composable
private fun BpTrackerApp(
    storageHost: StorageHost,
    notificationPermissionHost: NotificationPermissionHost,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var themeMenuOpen by remember { mutableStateOf(false) }
    var reminderDialogOpen by remember { mutableStateOf(false) }
    val tabs = AppTab.entries
    val context = LocalContext.current
    val reminderStore = remember { ReminderStore(context) }
    var reminderSettings by remember { mutableStateOf(reminderStore.get()) }
    val coroutineScope = rememberCoroutineScope()

    if (reminderDialogOpen) {
        ReminderSettingsDialog(
            settings = reminderSettings,
            onDismiss = { reminderDialogOpen = false },
            onSave = { newSettings ->
                reminderDialogOpen = false
                coroutineScope.launch {
                    if (newSettings.enabled) {
                        notificationPermissionHost.requestNotificationPermission()
                        ReminderScheduler.schedule(context, newSettings.times)
                    } else {
                        ReminderScheduler.cancel(context)
                    }
                    reminderStore.set(newSettings)
                    reminderSettings = newSettings
                }
            }
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.weight(1f),
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(tab.title, maxLines = 1) }
                        )
                    }
                }
                IconButton(onClick = { reminderDialogOpen = true }) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = "Daily reminder")
                }
                Box {
                    IconButton(onClick = { themeMenuOpen = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "Theme")
                    }
                    DropdownMenu(
                        expanded = themeMenuOpen,
                        onDismissRequest = { themeMenuOpen = false }
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                leadingIcon = {
                                    if (mode == themeMode) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    onThemeChange(mode)
                                    themeMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
            when (tabs[selectedTab]) {
                AppTab.CAPTURE -> CaptureScreen()
                AppTab.NOTE -> NoteScreen()
                AppTab.HISTORY -> HistoryScreen(storageHost = storageHost)
                AppTab.LOG -> LogScreen()
                AppTab.ABOUT -> AboutScreen()
            }
        }
    }
}
