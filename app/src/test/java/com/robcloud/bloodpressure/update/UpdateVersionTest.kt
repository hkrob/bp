package com.robcloud.bloodpressure.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun `higher minor is newer`() {
        assertTrue(UpdateManager.isNewer("2.1", "2.0"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(UpdateManager.isNewer("2.0", "2.0"))
    }

    @Test
    fun `lower version is not newer`() {
        assertFalse(UpdateManager.isNewer("1.9", "2.0"))
    }

    @Test
    fun `numeric compare beats string compare for double-digit minors`() {
        // "2.10" < "2.9" as strings, but 10 > 9 numerically.
        assertTrue(UpdateManager.isNewer("2.10", "2.9"))
        assertFalse(UpdateManager.isNewer("2.9", "2.10"))
    }

    @Test
    fun `leading v is tolerated`() {
        assertTrue(UpdateManager.isNewer("v2.1", "2.0"))
        assertFalse(UpdateManager.isNewer("v2.0", "2.0"))
    }

    @Test
    fun `major bump is newer`() {
        assertTrue(UpdateManager.isNewer("3.0", "2.9"))
    }

    @Test
    fun `differing component counts compare by value`() {
        assertTrue(UpdateManager.isNewer("2.0.1", "2.0"))
        assertFalse(UpdateManager.isNewer("2.0", "2.0.1"))
    }
}
