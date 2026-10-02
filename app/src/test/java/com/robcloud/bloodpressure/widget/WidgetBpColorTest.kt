package com.robcloud.bloodpressure.widget

import androidx.compose.ui.graphics.toArgb
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.ui.history.categoryColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetBpColorTest {
    @Test
    fun `normal stays white, not the darker green used elsewhere`() {
        assertEquals(0xFFFFFFFF.toInt(), widgetBpColor(BpCategory.NORMAL))
    }

    @Test
    fun `out-of-range categories use their usual status color`() {
        for (category in BpCategory.entries - BpCategory.NORMAL) {
            val expected = categoryColor(category).toArgb()
            assertEquals(category.toString(), expected, widgetBpColor(category))
            assertNotEquals(category.toString(), 0xFFFFFFFF.toInt(), widgetBpColor(category))
        }
    }
}
