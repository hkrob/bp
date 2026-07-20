package com.robcloud.bloodpressure.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromEpochMilli(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromArmName(value: String?): Arm? = value?.let { Arm.valueOf(it) }

    @TypeConverter
    fun toArmName(arm: Arm?): String? = arm?.name

    @TypeConverter
    fun fromLocalDateString(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun toLocalDateString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun fromNoteTypeName(value: String?): NoteType? = value?.let { NoteType.valueOf(it) }

    @TypeConverter
    fun toNoteTypeName(noteType: NoteType?): String? = noteType?.name
}
