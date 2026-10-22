package com.robcloud.bloodpressure.ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

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
 * Runs on-device text recognition (ML Kit's bundled Latin model — no network, no Play Services)
 * on the photo at [uri] and turns it into a best-effort reading via [parseReadingTokens]. The
 * caller must treat every field as a suggestion: show it for the user to check, never save it
 * unreviewed (see [ReadingOcrResult]).
 */
suspend fun scanReadingPhoto(context: Context, uri: Uri): ReadingOcrResult {
    val image = InputImage.fromFilePath(context, uri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val text = suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
        val tokens = text.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .map { element -> OcrToken(element.text, element.boundingBox?.top ?: 0, element.boundingBox?.height() ?: 0) }
        return parseReadingTokens(tokens)
    } finally {
        recognizer.close()
    }
}
