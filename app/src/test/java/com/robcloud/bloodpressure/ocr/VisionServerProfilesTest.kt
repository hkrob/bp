package com.robcloud.bloodpressure.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionServerProfilesTest {
    @Test
    fun `round-trips a profile with an API key`() {
        val profile = VisionServerProfile("1", "Gemini Flash", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "secret")
        assertEquals(listOf(profile), decodeProfiles(encodeProfiles(listOf(profile))))
    }

    @Test
    fun `round-trips a profile with no API key`() {
        val profile = VisionServerProfile("1", "Home Ollama", "http://10.1.10.84:11434", "qwen2.5vl", null)
        assertEquals(listOf(profile), decodeProfiles(encodeProfiles(listOf(profile))))
    }

    @Test
    fun `preserves order across multiple profiles`() {
        val profiles = listOf(
            VisionServerProfile("1", "A", "http://a", "m", null),
            VisionServerProfile("2", "B", "http://b", "m", "k"),
            VisionServerProfile("3", "C", "http://c", "m", null)
        )
        assertEquals(profiles, decodeProfiles(encodeProfiles(profiles)))
    }

    @Test
    fun `blank input decodes to no profiles`() {
        assertTrue(decodeProfiles("").isEmpty())
        assertTrue(decodeProfiles("   ").isEmpty())
    }

    @Test
    fun `malformed json decodes to no profiles instead of throwing`() {
        assertTrue(decodeProfiles("not json").isEmpty())
        assertTrue(decodeProfiles("""{"not": "an array"}""").isEmpty())
        assertTrue(decodeProfiles("""[{"id": "1"}]""").isEmpty()) // missing required fields
    }

    @Test
    fun `an empty list round-trips`() {
        assertTrue(decodeProfiles(encodeProfiles(emptyList())).isEmpty())
    }
}
