package com.robcloud.bloodpressure.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the notes/deleted_notes tables without touching readings/deleted_readings —
 * fallbackToDestructiveMigration below would otherwise wipe real reading history on
 * every schema bump that lacks an explicit migration.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `noteType` TEXT NOT NULL, `details` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_notes` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

@Database(
    entities = [Reading::class, DeletedReading::class, Note::class, DeletedNote::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bp-tracker.db"
                ).addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    // TRUNCATE keeps every committed write inside the single .db file
                    // (no -wal side file), so Android auto-backup — which is configured
                    // to include exactly bp-tracker.db — always captures a complete,
                    // consistent snapshot. Perf cost is irrelevant at this data size.
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build().also { instance = it }
            }
    }
}
