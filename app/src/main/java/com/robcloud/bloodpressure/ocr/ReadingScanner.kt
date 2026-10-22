package com.robcloud.bloodpressure.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TIMEOUT_MS = 30_000

// Most vision models cap their effective input resolution well below a modern phone photo (the
// sample this was built against was 2129x2501) and resize down to it internally regardless — so
// sending the original mostly just costs upload time and server-side decode work. 1568px on the
// long edge is the point past which several vision model vendors (Claude's own docs among them)
// document no further accuracy gain; this task needs the detail a too-aggressive downscale would
// lose (see ReadingOcr.kt's history), so this stays well above a "minimum size" crop.
private const val MAX_EDGE_PX = 1568
private const val JPEG_QUALITY = 90

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
    val imageBytes = preparePhotoBytes(context, uri)
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

    val conn = (URL(chatCompletionsUrl(config.baseUrl)).openConnection() as HttpURLConnection).apply {
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

/**
 * Reads the photo at [uri], corrects it to upright (a photo taken in portrait is commonly stored
 * as landscape pixels plus an EXIF rotation tag — sending it as-is could mean the model sees it
 * sideways), downscales it to [MAX_EDGE_PX] if it's larger, and re-encodes as JPEG. Decoding a
 * whole [Bitmap] isn't unit-testable without Robolectric; [scaledSize] is, and holds the actual
 * sizing decision.
 */
private fun preparePhotoBytes(context: Context, uri: Uri): ByteArray {
    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IOException("Could not read the photo")
    val orientation = ByteArrayInputStream(rawBytes).use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
    val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        ?: throw IOException("Could not decode the photo")
    val upright = rotateForExifOrientation(decoded, orientation)
    val (targetWidth, targetHeight) = scaledSize(upright.width, upright.height, MAX_EDGE_PX)
    val resized = if (targetWidth == upright.width && targetHeight == upright.height) {
        upright
    } else {
        Bitmap.createScaledBitmap(upright, targetWidth, targetHeight, true)
    }
    return ByteArrayOutputStream().use { out ->
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
}

private fun rotateForExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * The chat-completions endpoint for [baseUrl], handling either convention a server's "base URL"
 * might mean: some (Ollama, LM Studio) document theirs already including "/v1"; a bare host:port
 * is just as reasonable a thing to type into the settings field. Ensures "/v1" appears exactly
 * once either way, so "http://host:11434" and "http://host:11434/v1" both resolve to the same
 * request — entering the field either way (as a user did, triggering a 404 from the duplicated
 * "/v1/v1" the old unconditional-append version produced) now works.
 */
internal fun chatCompletionsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    val withV1 = if (trimmed.endsWith("/v1", ignoreCase = true)) trimmed else "$trimmed/v1"
    return "$withV1/chat/completions"
}

/**
 * [width]x[height] scaled down to fit within [maxEdge] on its longer side, preserving aspect
 * ratio — or unchanged if already within it. Never upscales.
 */
internal fun scaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / longest
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}
