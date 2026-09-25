package com.robcloud.bloodpressure.data

import androidx.room.ColumnInfo
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
    val takenAt: Instant,
    /**
     * The monitor flagged an irregular heartbeat for this reading. The column default must match
     * MIGRATION_5_6, or Room's schema check fails at open.
     */
    @ColumnInfo(defaultValue = "0") val irregularHeartbeat: Boolean = false
)
