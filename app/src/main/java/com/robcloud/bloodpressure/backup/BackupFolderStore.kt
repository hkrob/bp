package com.robcloud.bloodpressure.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.time.Instant

private const val PREFS_NAME = "backup_prefs"
private const val KEY_FOLDER_URI = "backup_folder_uri"
private const val KEY_FOLDER_NAME = "backup_folder_name"
private const val KEY_LAST_SYNCED_AT = "last_synced_at"

/**
 * Remembers the folder the user picked via the system folder picker (Storage Access
 * Framework) to back up readings.csv into — usually a folder inside their Google Drive,
 * but SAF lets them pick any provider (Dropbox, local storage, etc.) just as easily.
 */
class BackupFolderStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): Uri? = prefs.getString(KEY_FOLDER_URI, null)?.let(Uri::parse)

    fun set(uri: Uri) {
        val previous = get()
        if (previous != null && previous != uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    previous,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        // Cache the name now, while the permission is fresh and resolvable, so the History
        // status line can still show it later even if live resolution fails (see displayName).
        val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        prefs.edit {
            putString(KEY_FOLDER_URI, uri.toString())
            putString(KEY_FOLDER_NAME, name)
        }
    }

    /**
     * Best-effort folder name for the History status line. Returns null ONLY when no folder is
     * configured. Live name resolution through the Drive document provider can return null at
     * startup (it needs network / a warm provider) even when the folder URI and permission are
     * intact — so fall back to the name cached at pick time, then a generic label. This stops
     * the app from wrongly reporting a configured backup as "No backup folder chosen yet".
     */
    fun displayName(): String? {
        val uri = get() ?: return null
        val live = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        if (live != null) {
            if (prefs.getString(KEY_FOLDER_NAME, null) != live) {
                prefs.edit { putString(KEY_FOLDER_NAME, live) }
            }
            return live
        }
        return prefs.getString(KEY_FOLDER_NAME, null) ?: "your backup folder"
    }

    fun getLastSyncedAt(): Instant? =
        prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    fun setLastSyncedAt(instant: Instant) {
        prefs.edit { putLong(KEY_LAST_SYNCED_AT, instant.toEpochMilli()) }
    }
}
