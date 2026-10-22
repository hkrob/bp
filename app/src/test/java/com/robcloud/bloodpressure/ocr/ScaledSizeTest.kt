package com.robcloud.bloodpressure.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaledSizeTest {
    @Test
    fun `leaves an image already within the cap unchanged`() {
        assertEquals(800 to 600, scaledSize(800, 600, 1568))
    }

    @Test
    fun `downscales a portrait photo, preserving aspect ratio`() {
        // The actual sample this was built against: 2129x2501.
        val (w, h) = scaledSize(2129, 2501, 1568)
        assertEquals(1568, h) // the longer edge lands exactly on the cap
        assertEquals((2129.0 / 2501.0 * 1568).toInt(), w)
    }

    @Test
    fun `downscales a landscape photo the same way, on whichever edge is longer`() {
        val (w, h) = scaledSize(2501, 2129, 1568)
        assertEquals(1568, w)
        assertEquals((2129.0 / 2501.0 * 1568).toInt(), h)
    }

    @Test
    fun `never upscales a small photo`() {
        assertEquals(200 to 150, scaledSize(200, 150, 1568))
    }

    @Test
    fun `a square image at exactly the cap is unchanged`() {
        assertEquals(1568 to 1568, scaledSize(1568, 1568, 1568))
    }
}
