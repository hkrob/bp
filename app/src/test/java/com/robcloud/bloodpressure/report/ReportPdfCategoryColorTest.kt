package com.robcloud.bloodpressure.report

import com.robcloud.bloodpressure.data.BpCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The PDF report keeps its own copy of the category colors (a plain [Int], not a Compose
 * [androidx.compose.ui.graphics.Color]) instead of sharing ui/history/ReadingsTable.kt's
 * `categoryColor`, so nothing catches it drifting from ui/theme/Color.kt's
 * StatusLow/StatusNormal/StatusElevated/StatusHigh except a test like this one. Update both
 * sides together if either changes.
 */
class ReportPdfCategoryColorTest {
    private val expected = mapOf(
        BpCategory.LOW to 0xFF2F80ED.toInt(), // StatusLow
        BpCategory.NORMAL to 0xFF2E7D32.toInt(), // StatusNormal
        BpCategory.ELEVATED to 0xFFF9A825.toInt(), // StatusElevated
        BpCategory.STAGE_1 to 0xFFF9A825.toInt(), // StatusElevated
        BpCategory.STAGE_2 to 0xFFD32F2F.toInt(), // StatusHigh
        BpCategory.CRISIS to 0xFFD32F2F.toInt() // StatusHigh
    )

    @Test
    fun `every category matches the app's canonical status color`() {
        for (category in BpCategory.entries) {
            assertEquals(
                "ReportPdf.categoryColor($category) should match ui theme's status color",
                expected.getValue(category),
                ReportPdf.categoryColor(category)
            )
        }
    }
}
