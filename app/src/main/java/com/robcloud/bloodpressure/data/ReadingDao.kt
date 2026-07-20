package com.robcloud.bloodpressure.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: DeletedReading)

    @Query("SELECT id FROM deleted_readings")
    suspend fun getTombstoneIds(): List<String>

    @Query("DELETE FROM deleted_readings WHERE id IN (:ids)")
    suspend fun clearTombstones(ids: List<String>)
}
