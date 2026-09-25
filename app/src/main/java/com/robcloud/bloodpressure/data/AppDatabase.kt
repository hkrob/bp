package com.robcloud.bloodpressure.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.time.Instant

const val DB_NAME = "bp-tracker.db"
const val DB_VERSION = 6

/**
 * Adds the notes/deleted_notes tables without touching readings/deleted_readings, so an
 * upgrade from v3 keeps the reading history.
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

/**
 * Adds a `time` column to notes so Medication Taken notes can record the actual clock time (and
 * interleave with readings in the Log by time). SQLite requires a default when adding a NOT NULL
 * column to a table with existing rows; '00:01' matches [DEFAULT_NOTE_TIME] and the entity's
 * @ColumnInfo(defaultValue), so existing (non-medication) notes land just after midnight.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `time` TEXT NOT NULL DEFAULT '00:01'")
    }
}

/**
 * Adds the irregular-heartbeat flag to readings. Existing readings default to 0 (not flagged),
 * matching the entity's @ColumnInfo(defaultValue).
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `readings` ADD COLUMN `irregularHeartbeat` INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [Reading::class, DeletedReading::class, Note::class, DeletedNote::class],
    version = DB_VERSION,
    exportSchema = true
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
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase {
            // Keep a copy of the file before Room runs any migration, so a faulty migration in a
            // future release can be recovered from. Best effort: a failed copy must never stop
            // the database from opening.
            runCatching {
                backupBeforeUpgrade(
                    dbFile = context.getDatabasePath(DB_NAME),
                    backupDir = File(context.filesDir, "db-backups"),
                    targetVersion = DB_VERSION,
                    now = Instant.now()
                )
            }
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                // Only the pre-v3 schemas, which never had a migration path, may be dropped.
                // Any other missing migration now fails loudly at open instead of silently
                // wiping the reading history: a crash is fixed by shipping the migration and
                // installing over the top, a wipe cannot be undone. Downgrades fail the same way.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                // TRUNCATE keeps every committed write inside the single .db file
                // (no -wal side file), so Android auto-backup — which is configured
                // to include exactly bp-tracker.db — always captures a complete,
                // consistent snapshot. Perf cost is irrelevant at this data size.
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
        }
    }
}
