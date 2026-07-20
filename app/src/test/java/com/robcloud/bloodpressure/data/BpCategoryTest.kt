package com.robcloud.bloodpressure.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BpCategoryTest {

    @Test
    fun `AHA category boundaries`() {
        assertEquals(BpCategory.NORMAL, BpCategory.of(119, 79))
        assertEquals(BpCategory.ELEVATED, BpCategory.of(120, 79))
        assertEquals(BpCategory.ELEVATED, BpCategory.of(129, 79))
        assertEquals(BpCategory.STAGE_1, BpCategory.of(130, 79))
        assertEquals(BpCategory.STAGE_1, BpCategory.of(119, 80))
        assertEquals(BpCategory.STAGE_2, BpCategory.of(140, 79))
        assertEquals(BpCategory.STAGE_2, BpCategory.of(119, 90))
        assertEquals(BpCategory.CRISIS, BpCategory.of(180, 79))
        assertEquals(BpCategory.CRISIS, BpCategory.of(119, 120))
    }

    @Test
    fun `higher of the two numbers wins`() {
        // Normal systolic but crisis diastolic must classify as crisis, not normal.
        assertEquals(BpCategory.CRISIS, BpCategory.of(110, 125))
        // Elevated systolic but stage-2 diastolic must classify as stage 2.
        assertEquals(BpCategory.STAGE_2, BpCategory.of(125, 95))
    }
}
