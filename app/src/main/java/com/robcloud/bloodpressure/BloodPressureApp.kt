package com.robcloud.bloodpressure

import android.app.Application
import com.robcloud.bloodpressure.backup.BackupFolderStore
import com.robcloud.bloodpressure.backup.BackupSyncManager
import com.robcloud.bloodpressure.backup.BackupSyncWorker
import com.robcloud.bloodpressure.data.AppDatabase
import com.robcloud.bloodpressure.reminders.ReminderScheduler
import com.robcloud.bloodpressure.reminders.ReminderStore
import com.robcloud.bloodpressure.update.UpdatePrefsStore
import com.robcloud.bloodpressure.update.UpdateScheduler

class BloodPressureApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val backupFolderStore by lazy { BackupFolderStore(this) }
    val backupSyncManager by lazy { BackupSyncManager(this, database, backupFolderStore) }

    override fun onCreate() {
        super.onCreate()
        BackupSyncWorker.scheduleDaily(this)
        UpdateScheduler.schedule(this, UpdatePrefsStore(this).frequency)
        // Reminder settings survive a backup restore but WorkManager's queue doesn't, and older
        // versions scheduled reminders differently — make sure the next one is always armed.
        ReminderScheduler.ensureScheduled(this, ReminderStore(this).get())
    }
}
