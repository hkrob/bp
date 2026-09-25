package com.robcloud.bloodpressure.backup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.time.LocalDate

private const val PREFS_NAME = "backup_prefs"
private const val KEY_FOLDER_URI = "backup_folder_uri"
private const val KEY_FOLDER_NAME = "backup_folder_name"
private const val KEY_LAST_SYNCED_AT = "last_synced_at"
private const val KEY_LAST_ERROR = "last_sync_error"
private const val KEY_FAILING_SINCE = "sync_failing_since"
private const val KEY_LAST_SNAPSHOT_DATE = "last_snapshot_date"
private const val KEY_PENDING_NOTICE = "pending_notice"
private const val LOCAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/** A persisted SAF grant, reduced to what [hasReadWriteGrant] needs (testable without Android). */
data class GrantInfo(val uri: String, val read: Boolean, val write: Boolean)

fun hasReadWriteGrant(target: String, grants: List<GrantInfo>): Boolean =
    grants.any { it.uri == target && it.read && it.write }

/**
 * Remembers the folder the user picked via the system folder picker (Storage Access
 * Framework) to back up readings.csv into — usually a folder inside their Google Drive,
 * but SAF lets them pick any provider (Dropbox, local storage, etc.) just as easily.
 *
 * These prefs are included in Android backup, but the SAF permission behind the folder is not:
 * after a restore or device transfer the Uri comes back without access. [needsRelink] detects
 * that so the UI can ask the user to pick the folder again (by name) instead of failing silently.
 */
class BackupFolderStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): Uri? = prefs.getString(KEY_FOLDER_URI, null)?.let(Uri::parse)

    fun isConfigured(): Boolean = get() != null

    /** True when this app still holds a persisted read+write grant for the configured folder. */
    fun hasAccess(): Boolean {
        val uri = get() ?: return false
        val grants = context.contentResolver.persistedUriPermissions.map {
            GrantInfo(it.uri.toString(), it.isReadPermission, it.isWritePermission)
        }
        return hasReadWriteGrant(uri.toString(), grants)
    }

    /** A folder is configured but the app can no longer reach it — the user must pick it again. */
    fun needsRelink(): Boolean = isConfigured() && !hasAccess()

    /**
     * True only when a folder IS set but resolves to on-device storage (SAF's "external
     * storage" provider) — doesn't protect against device loss, app uninstall, or factory
     * reset the way a cloud provider (Drive, Dropbox, etc.) does. Any other authority is
     * presumed cloud-backed; no cloud provider authority is hardcoded here.
     */
    fun isLocalOnly(): Boolean = get()?.authority == LOCAL_STORAGE_AUTHORITY

    /**
     * Friendly name of the storage provider behind the configured folder (e.g. "Google Drive"),
     * or null when it's local storage or a provider not in the known list below — folder name
     * and sync time are still shown either way, this is purely an extra hint of *where*.
     */
    fun providerLabel(): String? = when (get()?.authority) {
        "com.google.android.apps.docs.storage", "com.google.android.apps.docs.storage.legacy" -> "Google Drive"
        "com.dropbox.product.android.dbapp.documentprovider" -> "Dropbox"
        "com.microsoft.skydrive.content.StorageAccessProvider" -> "OneDrive"
        "com.box.android.documents" -> "Box"
        else -> null
    }

    /**
     * Makes [uri] the backup folder. Takes the persistable grant for it and releases the previous
     * folder's. Clears sync bookkeeping that belonged to the old folder (or was restored from
     * another phone). Does provider IPC — call off the main thread.
     */
    fun set(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val previous = get()
        if (previous != null && previous != uri) {
            runCatching { context.contentResolver.releasePersistableUriPermission(previous, flags) }
        }
        // Cache the name now, while the permission is fresh and resolvable, so the History
        // status line can still show it later even if live resolution fails (see displayName).
        val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        prefs.edit {
            putString(KEY_FOLDER_URI, uri.toString())
            putString(KEY_FOLDER_NAME, name)
            remove(KEY_LAST_SYNCED_AT)
            remove(KEY_LAST_ERROR)
            remove(KEY_FAILING_SINCE)
            remove(KEY_LAST_SNAPSHOT_DATE)
        }
    }

    /** The folder name cached when it was picked — no provider call, safe on the main thread. */
    fun cachedName(): String? {
        if (get() == null) return null
        return prefs.getString(KEY_FOLDER_NAME, null) ?: "your backup folder"
    }

    /**
     * Best-effort folder name for the History status line. Returns null ONLY when no folder is
     * configured. Live name resolution through the Drive document provider can return null at
     * startup (it needs network / a warm provider) even when the folder URI and permission are
     * intact — so fall back to the name cached at pick time, then a generic label. This stops
     * the app from wrongly reporting a configured backup as "No backup folder chosen yet".
     * Does provider IPC — call off the main thread; use [cachedName] on it.
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
        return cachedName()
    }

    fun getLastSyncedAt(): Instant? =
        prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    /** Records a successful sync, which also ends any run of failures. */
    fun setLastSyncedAt(instant: Instant) {
        prefs.edit {
            putLong(KEY_LAST_SYNCED_AT, instant.toEpochMilli())
            remove(KEY_LAST_ERROR)
            remove(KEY_FAILING_SINCE)
        }
    }

    /** Records a failed sync. [getFailingSince] keeps the time of the first failure in a run. */
    fun recordFailure(message: String, now: Instant = Instant.now()) {
        prefs.edit {
            putString(KEY_LAST_ERROR, message)
            if (!prefs.contains(KEY_FAILING_SINCE)) putLong(KEY_FAILING_SINCE, now.toEpochMilli())
        }
    }

    fun getLastError(): String? = prefs.getString(KEY_LAST_ERROR, null)

    fun getFailingSince(): Instant? =
        prefs.getLong(KEY_FAILING_SINCE, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    /** A message from a background sync for the user, shown once by the History tab. */
    fun setPendingNotice(message: String) {
        prefs.edit { putString(KEY_PENDING_NOTICE, message) }
    }

    fun takePendingNotice(): String? {
        val notice = prefs.getString(KEY_PENDING_NOTICE, null) ?: return null
        prefs.edit { remove(KEY_PENDING_NOTICE) }
        return notice
    }

    fun getLastSnapshotDate(): LocalDate? =
        prefs.getString(KEY_LAST_SNAPSHOT_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun setLastSnapshotDate(date: LocalDate) {
        prefs.edit { putString(KEY_LAST_SNAPSHOT_DATE, date.toString()) }
    }

    /** Emits once on collection and again whenever any backup setting or sync status changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
