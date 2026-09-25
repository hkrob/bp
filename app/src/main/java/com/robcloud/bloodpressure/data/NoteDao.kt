package com.robcloud.bloodpressure.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /** Inserts only rows whose id isn't already stored — never overwrites a local edit. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(notes: List<Note>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: DeletedNote)

    /** See [ReadingDao.deleteWithTombstone]. */
    @Transaction
    suspend fun deleteWithTombstone(id: String) {
        deleteById(id)
        insertTombstone(DeletedNote(id))
    }

    /** See [ReadingDao.restore]. */
    @Transaction
    suspend fun restore(note: Note) {
        insert(note)
        clearTombstones(listOf(note.id))
    }

    @Query("SELECT id FROM deleted_notes")
    suspend fun getTombstoneIds(): List<String>

    @Query("DELETE FROM deleted_notes WHERE id IN (:ids)")
    suspend fun clearTombstones(ids: List<String>)

    @Transaction
    suspend fun clearTombstonesChunked(ids: List<String>) {
        ids.chunked(SQL_IN_CHUNK).forEach { clearTombstones(it) }
    }
}
