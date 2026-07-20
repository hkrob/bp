package com.robcloud.bloodpressure.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {

    @Test
    fun `typical reading is valid`() {
        assertNull(validateReading(systolic = 120, diastolic = 80, heartRate = 70))
    }

    @Test
    fun `boundary values are valid`() {
        assertNull(validateReading(60, 40, 30))
        assertNull(validateReading(260, 150, 220))
    }

    @Test
    fun `systolic at or below diastolic is rejected as swapped entry`() {
        assertNotNull(validateReading(80, 120, 70))
        assertNotNull(validateReading(80, 80, 70))
        assertNull(validateReading(81, 80, 70))
    }

    @Test
    fun `out of range or missing values are rejected`() {
        assertNotNull(validateReading(null, 80, 70))
        assertNotNull(validateReading(120, null, 70))
        assertNotNull(validateReading(120, 80, null))
        assertNotNull(validateReading(59, 80, 70))
        assertNotNull(validateReading(261, 80, 70))
        assertNotNull(validateReading(120, 39, 70))
        assertNotNull(validateReading(120, 151, 70))
        assertNotNull(validateReading(120, 80, 29))
        assertNotNull(validateReading(120, 80, 221))
    }
}
