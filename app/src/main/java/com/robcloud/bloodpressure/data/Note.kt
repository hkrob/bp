package com.robcloud.bloodpressure.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

const val NOTE_DETAILS_MAX_LENGTH = 500

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val noteType: NoteType,
    val details: String
)
