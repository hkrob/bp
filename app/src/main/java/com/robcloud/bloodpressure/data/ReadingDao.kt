package com.robcloud.bloodpressure.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * SQLite on Android 8-11 allows at most 999 bound variables per statement, so `IN (:ids)`
 * lists are split into chunks below that.
 */
internal const val SQL_IN_CHUNK = 500

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<Reading>>

    @Query("SELECT * FROM readings")
    suspend fun getAll(): List<Reading>

    @Query("SELECT * FROM readings ORDER BY takenAt DESC LIMIT 1")
    fun observeLatest(): Flow<Reading?>

    @Query("SELECT * FROM readings ORDER BY takenAt DESC LIMIT 2")
    fun observeLatestTwo(): Flow<List<Reading>>

    @Query("SELECT * FROM readings ORDER BY takenAt DESC LIMIT 1")
    suspend fun getLatest(): Reading?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: Reading)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<Reading>)

    /** Inserts only rows whose id isn't already stored — never overwrites a local edit. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(readings: List<Reading>)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: DeletedReading)

    /**
     * Deletes the reading and records its tombstone in one transaction, so a backup sync can
     * never observe the row gone but not yet tombstoned (and re-import it from the CSV).
     */
    @Transaction
    suspend fun deleteWithTombstone(id: String) {
        deleteById(id)
        insertTombstone(DeletedReading(id))
    }

    @Query("SELECT id FROM deleted_readings")
    suspend fun getTombstoneIds(): List<String>

    @Query("DELETE FROM deleted_readings WHERE id IN (:ids)")
    suspend fun clearTombstones(ids: List<String>)

    @Transaction
    suspend fun clearTombstonesChunked(ids: List<String>) {
        ids.chunked(SQL_IN_CHUNK).forEach { clearTombstones(it) }
    }
}
