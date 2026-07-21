package com.robcloud.bloodpressure.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

const val NOTE_DETAILS_MAX_LENGTH = 500

/**
 * Non-medication notes have no meaningful clock time, so they default to 00:01 — just after
 * midnight, which keeps them at the top of their day in the time-ordered Log. Medication Taken
 * notes instead carry the actual time they were logged. Stored default must match the Room
 * column default in the migration (see MIGRATION_4_5) and @ColumnInfo below.
 */
val DEFAULT_NOTE_TIME: LocalTime = LocalTime.of(0, 1)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val noteType: NoteType,
    val details: String,
    @ColumnInfo(defaultValue = "00:01") val time: LocalTime = DEFAULT_NOTE_TIME
)
