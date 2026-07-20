package com.robcloud.bloodpressure.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class Arm {
    LEFT,
    RIGHT
}

@Entity(tableName = "readings")
data class Reading(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val systolicMmHg: Int,
    val diastolicMmHg: Int,
    val heartRateBpm: Int,
    val arm: Arm,
    val takenAt: Instant
)
