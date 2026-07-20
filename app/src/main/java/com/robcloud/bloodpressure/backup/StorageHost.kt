package com.robcloud.bloodpressure.backup

import android.net.Uri

/**
 * Bridges suspend-based Storage Access Framework flows to an Activity's
 * ActivityResultLaunchers, since launching system pickers requires an Activity.
 * Implemented by MainActivity. All return null if the user cancels the picker.
 */
interface StorageHost {
    /** System folder picker — used to choose the backup folder, and the export destination folder. */
    suspend fun pickFolder(): Uri?

    /** System file picker — used for Import CSV. */
    suspend fun openDocument(): Uri?
}
