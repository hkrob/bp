package com.robcloud.bloodpressure.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BackupStatusTest {

    private val now = Instant.parse("2026-09-25T09:00:00Z")
    private val healthy = BackupStatus(folderName = "BP", providerLabel = "Google Drive", lastSyncedAt = now)

    @Test
    fun `healthy cloud backup shows no banner`() {
        assertNull(backupBannerMessage(healthy, now))
    }

    @Test
    fun `no folder beats every other problem`() {
        val message = backupBannerMessage(BackupStatus(localOnly = true, lastError = "x", failingSince = now.minusSeconds(999_999)), now)
        assertTrue(message!!.startsWith("No backup folder set"))
    }

    @Test
    fun `lost access asks to re-link by folder name`() {
        val message = backupBannerMessage(healthy.copy(needsRelink = true, localOnly = true), now)
        assertTrue(message!!.contains("Re-link"))
        assertTrue(message.contains("\"BP\""))
    }

    @Test
    fun `failures only warn once they have lasted a day`() {
        val failing = healthy.copy(lastError = "Could not list the backup folder.")
        assertNull(backupBannerMessage(failing.copy(failingSince = now.minus(Duration.ofHours(23))), now))
        val message = backupBannerMessage(failing.copy(failingSince = now.minus(Duration.ofHours(24))), now)
        assertEquals(
            "Backups have been failing for over a day: Could not list the backup folder. See the History tab.",
            message
        )
    }

    @Test
    fun `local-only storage warns last`() {
        assertTrue(backupBannerMessage(healthy.copy(localOnly = true), now)!!.contains("local storage"))
    }

    @Test
    fun `grant must match the folder and allow both read and write`() {
        val target = "content://com.android.externalstorage.documents/tree/primary%3ABP"
        assertTrue(hasReadWriteGrant(target, listOf(GrantInfo(target, read = true, write = true))))
        assertFalse(hasReadWriteGrant(target, listOf(GrantInfo(target, read = true, write = false))))
        assertFalse(hasReadWriteGrant(target, listOf(GrantInfo("$target%2Fother", read = true, write = true))))
        assertFalse(hasReadWriteGrant(target, emptyList()))
    }
}
