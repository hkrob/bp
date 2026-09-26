package com.robcloud.bloodpressure.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant

/**
 * Where the app looks for updates. Each GitHub Release must be tagged with the version name
 * (e.g. `v2.1`) and have the RELEASE-signed `BPTracker-vX.Y.apk` attached as an asset — Android
 * only installs an update signed with the same key as the current install.
 */
object UpdateConfig {
    // The public GitHub repo that hosts the release APKs (github.com/hkrob/bp).
    const val OWNER = "hkrob"
    const val REPO = "bp"

    val latestReleaseApiUrl: String
        get() = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** The project's page on GitHub, linked from the About tab. */
    val projectUrl: String
        get() = "https://github.com/$OWNER/$REPO"

    val isConfigured: Boolean get() = OWNER != "OWNER" && REPO != "REPO"
}

/** GitHub answered, but not with a release; [message] is written for the user. */
class UpdateCheckException(message: String) : Exception(message)

/**
 * Explains a non-200 answer from the releases API. Unauthenticated calls are limited to 60 an
 * hour per IP address, shared by every device on the network, and GitHub reports running out as
 * a 403 (or 429) with `X-RateLimit-Remaining: 0` and the reset time in `X-RateLimit-Reset`
 * (epoch seconds).
 */
internal fun updateCheckFailureMessage(code: Int, rateLimitRemaining: String?, rateLimitReset: String?, now: Instant): String {
    val limited = code == 429 || (code == 403 && rateLimitRemaining?.trim() == "0")
    return when {
        limited -> {
            val reset = rateLimitReset?.trim()?.toLongOrNull()?.let(Instant::ofEpochSecond)
            val wait = reset?.let { Duration.between(now, it) }
            val minutes = wait?.let { ((it.seconds + 59) / 60).coerceAtLeast(1) }
            "GitHub's hourly limit on update checks has been reached (it's shared by every device on " +
                "your network). " + if (minutes != null) "Try again in about $minutes min." else "Try again later."
        }
        code == 404 -> "No published release was found on GitHub"
        else -> "GitHub couldn't answer the update check (HTTP $code). Try again later."
    }
}

data class ReleaseInfo(
    val versionName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val notes: String
)

/**
 * Self-update over GitHub Releases: check the latest published version, download its APK, and
 * hand it to the system installer. No third-party libraries — HttpURLConnection + org.json.
 */
object UpdateManager {
    private const val TIMEOUT_MS = 15_000

    /**
     * Latest published release, or null if it has no APK asset. Throws [UpdateCheckException] when
     * GitHub answers with an error (including its rate limit), and an IOException when it can't be
     * reached. Runs off the main thread.
     */
    suspend fun checkLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(UpdateConfig.latestReleaseApiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BP-Tracker") // GitHub rejects requests without a UA
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw UpdateCheckException(
                    updateCheckFailureMessage(
                        code,
                        conn.getHeaderField("X-RateLimit-Remaining"),
                        conn.getHeaderField("X-RateLimit-Reset"),
                        Instant.now()
                    )
                )
            }
            val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val versionName = obj.getString("tag_name").trim().removePrefix("v").removePrefix("V")
            // optString turns a JSON null into the text "null"; a release with no notes has body: null.
            val notes = if (obj.isNull("body")) "" else obj.optString("body", "")
            val assets = obj.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                    return@withContext ReleaseInfo(
                        versionName = versionName,
                        apkUrl = a.getString("browser_download_url"),
                        apkSizeBytes = a.optLong("size", 0L),
                        notes = notes
                    )
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * True when [latest] is a strictly higher version than [current]. Compares dotted numbers
     * component-wise (2.10 > 2.9, unlike a string compare), tolerating a leading "v" and any
     * non-numeric suffix on a component.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parseVersion(latest)
        val b = parseVersion(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split(".")
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** Downloads the release APK into cache/updates, reporting 0..100 progress. Returns the file. */
    suspend fun download(context: Context, release: ReleaseInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "BPTracker-${release.versionName}.apk")
            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true // GitHub redirects asset downloads to a CDN host
                setRequestProperty("User-Agent", "BP-Tracker")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                conn.connect()
                val total = if (release.apkSizeBytes > 0) release.apkSizeBytes else conn.contentLengthLong.toLong()
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var read: Int
                        var lastPct = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt()
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            out
        }

    /** Launches the system package installer for [file] (user confirms the install). */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Whether the app is currently allowed to install APKs ("install unknown apps"). */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user grants this app permission to install APKs. */
    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
