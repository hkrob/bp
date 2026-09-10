package com.robcloud.bloodpressure.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.robcloud.bloodpressure.ui.theme.BloodPressureTheme
import com.robcloud.bloodpressure.ui.theme.ThemeMode

/**
 * Renders [content] for a snapshot on a full-bleed themed surface, so the baseline also covers
 * the theme's background and any regression in it.
 *
 * [ThemeMode.SYSTEM] is deliberately not offered: it resolves to the device's dynamic colour
 * scheme, which is wallpaper-derived and therefore not a stable baseline.
 */
fun Paparazzi.snapshotOnSurface(
    name: String,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    snapshot(name = name) {
        BloodPressureTheme(themeMode = themeMode) {
            Surface(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(Modifier.fillMaxWidth()) { content() }
                }
            }
        }
    }
}
