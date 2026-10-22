package com.robcloud.bloodpressure.report

import androidx.compose.ui.graphics.toArgb
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.ui.history.categoryColor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ReportPdf.categoryColor] delegates to ui/history/ReadingsTable.kt's `categoryColor` (backed by
 * ui/theme/Color.kt's StatusLow/StatusNormal/StatusElevated/StatusHigh) rather than keeping its
 * own copy of the mapping. This pins that delegation — a reading showing a different shade in the
 * report than everywhere else in the app would be a regression — so it still catches drift if a
 * future change reintroduces a separate, hand-written mapping here.
 */
class ReportPdfCategoryColorTest {
    @Test
    fun `every category matches the app's canonical status color`() {
        for (category in BpCategory.entries) {
            assertEquals(
                "ReportPdf.categoryColor($category) should match ui theme's status color",
                categoryColor(category).toArgb(),
                ReportPdf.categoryColor(category)
            )
        }
    }
}
