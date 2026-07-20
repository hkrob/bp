package com.robcloud.bloodpressure

import android.app.Application
import com.robcloud.bloodpressure.backup.BackupFolderStore
import com.robcloud.bloodpressure.backup.BackupSyncManager
import com.robcloud.bloodpressure.backup.BackupSyncWorker
import com.robcloud.bloodpressure.data.AppDatabase

class BloodPressureApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val backupFolderStore by lazy { BackupFolderStore(this) }
    val backupSyncManager by lazy {
        BackupSyncManager(this, database.readingDao(), database.noteDao(), backupFolderStore)
    }

    override fun onCreate() {
        super.onCreate()
        BackupSyncWorker.scheduleDaily(this)
    }
}
