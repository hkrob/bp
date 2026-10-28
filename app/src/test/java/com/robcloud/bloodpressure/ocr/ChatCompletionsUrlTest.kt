package com.robcloud.bloodpressure.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCompletionsUrlTest {
    @Test
    fun `a bare host and port gets v1 added`() {
        assertEquals(
            "http://10.1.10.84:11434/v1/chat/completions",
            chatCompletionsUrl("http://10.1.10.84:11434")
        )
    }

    @Test
    fun `a base url that already ends in v1 isn't doubled up`() {
        // The exact case that produced a 404 in practice: the old code unconditionally appended
        // "/v1/chat/completions", so this became ".../v1/v1/chat/completions".
        assertEquals(
            "http://10.1.10.84:11434/v1/chat/completions",
            chatCompletionsUrl("http://10.1.10.84:11434/v1")
        )
    }

    @Test
    fun `trailing slashes on either form are tolerated`() {
        assertEquals(
            "http://10.1.10.84:11434/v1/chat/completions",
            chatCompletionsUrl("http://10.1.10.84:11434/")
        )
        assertEquals(
            "http://10.1.10.84:11434/v1/chat/completions",
            chatCompletionsUrl("http://10.1.10.84:11434/v1/")
        )
    }

    @Test
    fun `a differently-cased v1 is still recognised`() {
        assertEquals(
            "http://host:11434/V1/chat/completions",
            chatCompletionsUrl("http://host:11434/V1")
        )
    }
}
