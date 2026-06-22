package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IntruderAlertDao {
    @Query("SELECT * FROM intruder_alerts ORDER BY timestamp DESC")
    fun getAllAlertsFlow(): Flow<List<IntruderAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: IntruderAlert)

    @Delete
    suspend fun deleteAlert(alert: IntruderAlert)

    @Query("DELETE FROM intruder_alerts")
    suspend fun deleteAllAlerts()
}
