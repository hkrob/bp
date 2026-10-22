package com.robcloud.bloodpressure.ocr

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TIMEOUT_MS = 30_000

private const val SCAN_PROMPT =
    "This photo shows a blood pressure monitor's display. Reply with ONLY three numbers " +
        "separated by single spaces, in this exact order: systolic pressure, diastolic " +
        "pressure, heart rate (pulse). If you cannot read one of them, use 0 for that one. " +
        "Do not include units, labels, punctuation, or any other words — reply with only the " +
        "three numbers."

/** Thrown by [scanReadingPhoto] when no [VisionServerStore] config has been set yet. */
class VisionServerNotConfiguredException : IOException("Set up a scan server in the About tab first")

/**
 * A fresh destination for the camera to write a scan photo to (cacheDir/ocr, see file_paths.xml),
 * as a FileProvider content Uri the system camera app can write to without this app granting it
 * broader file access.
 */
fun newOcrPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
    val file = File(dir, "scan-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Sends the photo at [uri] to the self-hosted vision model configured in [VisionServerStore] —
 * any server that speaks the OpenAI chat-completions API with image input (Ollama, LM Studio,
 * llama.cpp's server, vLLM) — and turns its reply into a best-effort reading via
 * [parseVisionReply]. No third-party library: HttpURLConnection + org.json, matching
 * [com.robcloud.bloodpressure.update.UpdateManager]. The caller must treat every field as a
 * suggestion: show it for the user to check, never save it unreviewed (see [ReadingOcrResult]).
 *
 * On-device recognition (ML Kit's bundled text model) was tried first and dropped: it could not
 * read a real seven-segment LCD monitor display at all in testing — it missed the digits
 * entirely, or misread them as letters — so there was nothing worth keeping on-device for this.
 */
suspend fun scanReadingPhoto(context: Context, uri: Uri): ReadingOcrResult = withContext(Dispatchers.IO) {
    val config = VisionServerStore(context).config() ?: throw VisionServerNotConfiguredException()
    val imageBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IOException("Could not read the photo")
    val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)

    val requestBody = JSONObject().apply {
        put("model", config.model)
        put("temperature", 0)
        put(
            "messages",
            JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "content",
                        JSONArray()
                            .put(JSONObject().apply { put("type", "text"); put("text", SCAN_PROMPT) })
                            .put(
                                JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply { put("url", dataUrl) })
                                }
                            )
                    )
                }
            )
        )
    }

    val conn = (URL("${config.baseUrl}/v1/chat/completions").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        config.apiKey?.let { setRequestProperty("Authorization", "Bearer $it") }
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }
    try {
        conn.outputStream.use { it.write(requestBody.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
        val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IOException("Scan server answered with HTTP $code: ${responseText.take(200)}")
        }
        val reply = JSONObject(responseText)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        parseVisionReply(reply)
    } finally {
        conn.disconnect()
    }
}
