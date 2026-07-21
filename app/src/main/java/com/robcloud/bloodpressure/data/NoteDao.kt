package com.robcloud.bloodpressure.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY date DESC, time DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: DeletedNote)

    @Query("SELECT id FROM deleted_notes")
    suspend fun getTombstoneIds(): List<String>

    @Query("DELETE FROM deleted_notes WHERE id IN (:ids)")
    suspend fun clearTombstones(ids: List<String>)
}
