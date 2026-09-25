package com.robcloud.bloodpressure.backup

import java.time.Duration
import java.time.Instant

/** Snapshot of the backup configuration and sync health, shared by the History, Add reading and About tabs. */
data class BackupStatus(
    val folderName: String? = null,
    val needsRelink: Boolean = false,
    val localOnly: Boolean = false,
    val providerLabel: String? = null,
    val lastSyncedAt: Instant? = null,
    val lastError: String? = null,
    val failingSince: Instant? = null
) {
    val configured: Boolean get() = folderName != null
}

/** Reads the current status. [BackupFolderStore.hasAccess] is a system binder call: prefer off the main thread. */
fun BackupFolderStore.status(): BackupStatus = BackupStatus(
    folderName = cachedName(),
    needsRelink = needsRelink(),
    localOnly = isLocalOnly(),
    providerLabel = providerLabel(),
    lastSyncedAt = getLastSyncedAt(),
    lastError = getLastError(),
    failingSince = getFailingSince()
)

/** Background failures are only worth a banner once they've persisted this long (not a brief offline spell). */
private val FAILING_BANNER_AFTER: Duration = Duration.ofHours(24)

/**
 * Text for the Add reading tab's backup warning, or null when the backup is healthy. The most
 * serious problem wins: no folder, lost access, a day of failed syncs, then local-only storage.
 */
fun backupBannerMessage(status: BackupStatus, now: Instant): String? = when {
    !status.configured ->
        "No backup folder set — your readings only live on this device. Set one in the History tab."
    status.needsRelink ->
        "Lost access to the backup folder \"${status.folderName}\". Tap Re-link in the History tab and choose it again."
    status.failingSince != null && !status.failingSince.isAfter(now.minus(FAILING_BANNER_AFTER)) ->
        "Backups have been failing for over a day" +
            (status.lastError?.trimEnd('.')?.let { ": $it." } ?: ".") + " See the History tab."
    status.localOnly ->
        "Backup folder is local storage — won't survive device loss. Tap Change in the History tab to pick a cloud folder."
    else -> null
}
