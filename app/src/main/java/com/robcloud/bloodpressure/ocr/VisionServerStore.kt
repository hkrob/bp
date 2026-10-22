package com.robcloud.bloodpressure.ocr

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "vision_server_prefs"
private const val KEY_BASE_URL = "base_url"
private const val KEY_MODEL = "model"
private const val KEY_API_KEY = "api_key"

/** Everything needed to call the scan server; only constructed once both are set (see [VisionServerStore.config]). */
data class VisionServerConfig(val baseUrl: String, val model: String, val apiKey: String?)

/**
 * Where to find a self-hosted, OpenAI-chat-completions-compatible vision model — Ollama, LM
 * Studio, llama.cpp's server and vLLM all speak this API — to scan a reading photo with. Entirely
 * optional and entirely the user's own infrastructure: nothing is scanned anywhere, and no photo
 * leaves the device, until this is set from the About tab.
 */
class VisionServerStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_BASE_URL, value.trim().trimEnd('/')) }

    var model: String
        get() = prefs.getString(KEY_MODEL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_MODEL, value.trim()) }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_API_KEY, value.trim()) }

    /** Null until both a server URL and a model name are set. */
    fun config(): VisionServerConfig? {
        val url = baseUrl
        val model = model
        if (url.isEmpty() || model.isEmpty()) return null
        return VisionServerConfig(url, model, apiKey.ifBlank { null })
    }
}
