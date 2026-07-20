package com.robcloud.bloodpressure.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.time.Instant

private const val PREFS_NAME = "backup_prefs"
private const val KEY_FOLDER_URI = "backup_folder_uri"
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
        prefs.edit { putString(KEY_FOLDER_URI, uri.toString()) }
    }

    fun displayName(): String? {
        val uri = get() ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
    }

    fun getLastSyncedAt(): Instant? =
        prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    fun setLastSyncedAt(instant: Instant) {
        prefs.edit { putLong(KEY_LAST_SYNCED_AT, instant.toEpochMilli()) }
    }
}
