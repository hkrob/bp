package com.robcloud.bloodpressure.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.runtime.Composable
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.ui.theme.ThemeMode
import com.robcloud.bloodpressure.update.UpdateCheckFrequency
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot coverage for [EqualWidthSegmentedRow].
 *
 * This control exists because Material3's `SingleChoiceSegmentedButtonRow` sizes to
 * `itemCount × widest label` and overflows on narrow containers — a regression that was only
 * visible on-device and took several releases to settle. The cases below pin the properties
 * that were repeatedly broken: every cell the same width, the row filling its container
 * exactly, and an over-long label ellipsizing rather than wrapping and growing its cell.
 *
 * Baselines live in `app/src/test/snapshots/images`. Record with `recordPaparazziDebug`,
 * check with `verifyPaparazziDebug`.
 */
class SegmentedControlSnapshotTest {

    /** 393dp-wide portrait phone — the everyday case. */
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private val frequencies = UpdateCheckFrequency.entries.toList()

    private fun snapshot(
        name: String,
        themeMode: ThemeMode = ThemeMode.LIGHT,
        content: @Composable () -> Unit
    ) = paparazzi.snapshotOnSurface(name, themeMode, content)

    @Composable
    private fun FrequencyRow(selected: UpdateCheckFrequency = UpdateCheckFrequency.DAILY) {
        EqualWidthSegmentedRow(
            options = frequencies,
            selected = selected,
            label = { it.label },
            onSelect = {}
        )
    }

    // --- Themes -------------------------------------------------------------------------

    @Test
    fun frequencyRowLight() = snapshot("frequency-light", ThemeMode.LIGHT) { FrequencyRow() }

    @Test
    fun frequencyRowDark() = snapshot("frequency-dark", ThemeMode.DARK) { FrequencyRow() }

    /** Console theme swaps in a monospace typeface, which changes label widths. */
    @Test
    fun frequencyRowConsole() = snapshot("frequency-console", ThemeMode.CONSOLE) { FrequencyRow() }

    // --- Selection position -------------------------------------------------------------
    // The selected cell gains a check icon and raised z-index; neither may change its width.

    @Test
    fun frequencyRowFirstSelected() =
        snapshot("frequency-selected-first") { FrequencyRow(UpdateCheckFrequency.NEVER) }

    @Test
    fun frequencyRowLastSelected() =
        snapshot("frequency-selected-last") { FrequencyRow(UpdateCheckFrequency.MONTHLY) }

    // --- Option count ------------------------------------------------------------------
    // Corner rounding is index-dependent, so the 1- and 2-option shapes are distinct paths.

    @Test
    fun singleOption() = snapshot("options-single") {
        EqualWidthSegmentedRow(
            options = listOf("Only"),
            selected = "Only",
            label = { it },
            onSelect = {}
        )
    }

    @Test
    fun twoOptions() = snapshot("options-two") {
        EqualWidthSegmentedRow(
            options = listOf("Left", "Right"),
            selected = "Right",
            label = { it },
            onSelect = {}
        )
    }

    @Test
    fun fourThemeOptions() = snapshot("options-theme-modes") {
        EqualWidthSegmentedRow(
            options = ThemeMode.entries.toList(),
            selected = ThemeMode.CONSOLE,
            label = { it.label },
            onSelect = {}
        )
    }

    // --- Overflow ----------------------------------------------------------------------

    /**
     * "Stage 2 hypertension" cannot fit a quarter-width cell at any sane size. It must
     * ellipsize on one line; if this baseline ever shows wrapped text or cells of unequal
     * width, the Material3 sizing bug has returned.
     */
    @Test
    fun longLabelsEllipsize() = snapshot("labels-long-ellipsize") {
        EqualWidthSegmentedRow(
            options = BpCategory.entries.toList(),
            selected = BpCategory.STAGE_2,
            label = { it.label },
            onSelect = {}
        )
    }
}
