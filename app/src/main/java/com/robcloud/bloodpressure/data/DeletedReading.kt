package com.robcloud.bloodpressure.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tombstone for a deleted reading. The backup sync merges the CSV file with the local
 * database by id, so without a record of the deletion the CSV copy would just re-import
 * the reading on the next sync.
 */
@Entity(tableName = "deleted_readings")
data class DeletedReading(
    @PrimaryKey val id: String
)
