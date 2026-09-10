package com.robcloud.bloodpressure.ui

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.robcloud.bloodpressure.update.UpdateCheckFrequency
import org.junit.Rule
import org.junit.Test

private val FREQUENCIES = UpdateCheckFrequency.entries.toList()

/** The update-check frequency row, the control whose sizing regressed repeatedly. */
@Composable
private fun FrequencyRow() {
    EqualWidthSegmentedRow(
        options = FREQUENCIES,
        selected = UpdateCheckFrequency.DAILY,
        label = { it.label },
        onSelect = {}
    )
}

/**
 * A cramped 320dp-wide phone. Four cells leave roughly 70dp each — well under the intrinsic
 * width of "Monthly" plus the selected cell's check icon. This is the configuration where the
 * Material3 control wrapped its longest label onto two lines and grew that one cell.
 */
class SegmentedControlNarrowSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 880, screenHeight = 1800)
    )

    @Test
    fun frequencyRowOnNarrowScreen() =
        paparazzi.snapshotOnSurface("frequency-narrow") { FrequencyRow() }
}

/**
 * Landscape. The row has width to spare here, which is why the bug stayed hidden in landscape
 * while portrait was broken — so this is the control case that must look identical in shape.
 */
class SegmentedControlLandscapeSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 2340,
            screenHeight = 1080,
            orientation = ScreenOrientation.LANDSCAPE
        )
    )

    @Test
    fun frequencyRowInLandscape() =
        paparazzi.snapshotOnSurface("frequency-landscape") { FrequencyRow() }
}

/**
 * Largest accessibility font scale. Labels grow but cells must not: the text ellipsizes and
 * the row keeps its fixed 40dp height and four equal columns.
 */
class SegmentedControlLargeFontSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 2.0f))

    @Test
    fun frequencyRowAtLargestFontScale() =
        paparazzi.snapshotOnSurface("frequency-font-scale-2x") { FrequencyRow() }
}
