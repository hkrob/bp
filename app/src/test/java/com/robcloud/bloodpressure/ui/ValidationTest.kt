package com.robcloud.bloodpressure.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `two-digit diastolic advances so heart rate is focused automatically`() {
        // The everyday case that used to require tapping Heart rate by hand.
        assertTrue(isFieldComplete("76", DIASTOLIC_MAX))
        assertTrue(isFieldComplete("89", DIASTOLIC_MAX))
    }

    @Test
    fun `diastolic that could still reach a valid three-digit value waits`() {
        // 100-150 are valid diastolics, so these prefixes must not steal focus early.
        assertFalse(isFieldComplete("10", DIASTOLIC_MAX))
        assertFalse(isFieldComplete("15", DIASTOLIC_MAX))
        assertTrue(isFieldComplete("16", DIASTOLIC_MAX)) // 160+ is impossible — done
    }

    @Test
    fun `three digits always advances`() {
        assertTrue(isFieldComplete("120", SYSTOLIC_MAX))
        assertTrue(isFieldComplete("150", DIASTOLIC_MAX))
    }

    @Test
    fun `systolic keeps waiting through prefixes of valid three-digit readings`() {
        assertFalse(isFieldComplete("1", SYSTOLIC_MAX))
        assertFalse(isFieldComplete("12", SYSTOLIC_MAX))  // typing 120
        assertFalse(isFieldComplete("26", SYSTOLIC_MAX))  // typing 260
        assertTrue(isFieldComplete("27", SYSTOLIC_MAX))   // 270+ is impossible — done
    }

    @Test
    fun `empty or partial input never advances`() {
        assertFalse(isFieldComplete("", DIASTOLIC_MAX))
        assertFalse(isFieldComplete("8", DIASTOLIC_MAX))
    }
}
