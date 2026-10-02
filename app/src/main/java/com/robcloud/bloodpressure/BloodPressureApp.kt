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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BloodPressureApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val backupFolderStore by lazy { BackupFolderStore(this) }
    val backupSyncManager by lazy { BackupSyncManager(this, database, backupFolderStore) }

    override fun onCreate() {
        super.onCreate()
        // None of these three need to finish before the first frame — each is prefs reads plus
        // WorkManager's own (binder-backed) scheduling — so don't make every cold start pay for
        // them serially on the main thread.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            BackupSyncWorker.scheduleDaily(this@BloodPressureApp)
            UpdateScheduler.schedule(this@BloodPressureApp, UpdatePrefsStore(this@BloodPressureApp).frequency)
            // Reminder settings survive a backup restore but WorkManager's queue doesn't, and
            // older versions scheduled reminders differently — make sure the next one is armed.
            ReminderScheduler.ensureScheduled(this@BloodPressureApp, ReminderStore(this@BloodPressureApp).get())
        }
    }
}
