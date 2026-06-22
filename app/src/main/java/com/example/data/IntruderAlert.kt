package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intruder_alerts")
data class IntruderAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val photoPath: String,
    val attemptedPackage: String?,
    val lockType: String
)
