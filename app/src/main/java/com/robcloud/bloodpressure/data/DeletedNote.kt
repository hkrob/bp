package com.robcloud.bloodpressure.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_notes")
data class DeletedNote(@PrimaryKey val id: String)
