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
        // WorkManager's own scheduling — so don't make every cold start pay for them on the main
        // thread. Each runs as its own coroutine instead of one after another: they don't depend
        // on each other, and sequencing them meant one throwing skipped the rest. SupervisorJob
        // stops a child's failure from cancelling its siblings, but an exception that reaches a
        // launch body uncaught still crashes the process via the dispatcher thread's default
        // handler, so each is wrapped too. All three are safe to call on every launch (that's the
        // point — WorkManager's queue doesn't survive things like a backup restore, and these
        // calls are how it gets re-armed), so missing this run on a cold start killed within
        // milliseconds of returning here is recovered by the next one, not left broken.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch { runCatching { BackupSyncWorker.scheduleDaily(this@BloodPressureApp) } }
        scope.launch {
            runCatching {
                UpdateScheduler.schedule(this@BloodPressureApp, UpdatePrefsStore(this@BloodPressureApp).frequency)
            }
        }
        scope.launch {
            // Reminder settings survive a backup restore but WorkManager's queue doesn't, and
            // older versions scheduled reminders differently — make sure the next one is armed.
            runCatching {
                ReminderScheduler.ensureScheduled(this@BloodPressureApp, ReminderStore(this@BloodPressureApp).get())
            }
        }
    }
}
