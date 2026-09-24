package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(entity: TelemetryEntity): Long

    @Query("SELECT * FROM telemetry_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTelemetry(limit: Int): Flow<List<TelemetryEntity>>

    @Query("SELECT * FROM telemetry_records ORDER BY timestamp DESC")
    fun getAllTelemetry(): Flow<List<TelemetryEntity>>

    @Query("SELECT * FROM telemetry_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTelemetry(): TelemetryEntity?

    @Query("DELETE FROM telemetry_records")
    suspend fun clearAllTelemetry()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWateringEvent(event: WateringEventEntity): Long

    @Query("SELECT * FROM watering_events ORDER BY startTime DESC LIMIT :limit")
    fun getWateringEvents(limit: Int = 50): Flow<List<WateringEventEntity>>

    @Query("DELETE FROM watering_events")
    suspend fun clearAllWateringEvents()
}
