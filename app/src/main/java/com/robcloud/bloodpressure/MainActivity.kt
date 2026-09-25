package com.robcloud.bloodpressure

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.robcloud.bloodpressure.reminders.NotificationHelper
import com.robcloud.bloodpressure.reminders.ReminderScheduler
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

// The scrims androidx.activity uses by default for 3-button navigation.
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private const val REMINDERS_BLOCKED_MESSAGE =
    "Notifications are blocked for BP Tracker, so reminders won't appear. Allow them in system settings."

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themeStore = ThemeStore(this)
        setContent {
            var themeMode by remember { mutableStateOf(themeStore.get()) }
            val darkTheme = when (themeMode) {
                ThemeMode.DARK, ThemeMode.CONSOLE -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Status/navigation bar icons must contrast with the app's theme, which can differ
            // from the system's (e.g. Dark or Console chosen on a light-mode phone).
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(DARK_SCRIM)
                    } else {
                        SystemBarStyle.light(LIGHT_SCRIM, DARK_SCRIM)
                    }
                )
                onDispose {}
            }
            BloodPressureTheme(themeMode) {
                BpTrackerApp(
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
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var themeMenuOpen by remember { mutableStateOf(false) }
    var reminderDialogOpen by rememberSaveable { mutableStateOf(false) }
    val tabs = AppTab.entries
    val context = LocalContext.current
    val reminderStore = remember { ReminderStore(context) }
    var reminderSettings by remember { mutableStateOf(reminderStore.get()) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, REMINDERS_BLOCKED_MESSAGE, Toast.LENGTH_LONG).show()
    }

    if (reminderDialogOpen) {
        ReminderSettingsDialog(
            settings = reminderSettings,
            onDismiss = { reminderDialogOpen = false },
            onSave = { newSettings ->
                reminderDialogOpen = false
                // Save and schedule first, so nothing is lost if the Activity is recreated while
                // the permission prompt is showing.
                reminderStore.set(newSettings)
                reminderSettings = newSettings
                if (newSettings.enabled) {
                    ReminderScheduler.schedule(context, newSettings.times)
                    if (!NotificationHelper.canShowReminders(context)) {
                        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        if (canAsk) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Toast.makeText(context, REMINDERS_BLOCKED_MESSAGE, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    ReminderScheduler.cancel(context)
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
                AppTab.HISTORY -> HistoryScreen()
                AppTab.LOG -> LogScreen()
                AppTab.ABOUT -> AboutScreen()
            }
        }
    }
}
