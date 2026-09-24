package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.PumpStatus
import com.example.data.model.TelemetryData

@Entity(tableName = "telemetry_records")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val temperature: Float,
    val humidity: Float,
    val soilMoisture: Int,
    val pumpStatus: String,
    val isSimulated: Boolean
) {
    fun toDomain(): TelemetryData {
        return TelemetryData(
            temperature = temperature,
            humidity = humidity,
            soilMoisture = soilMoisture,
            pumpStatus = if (pumpStatus == "WATERING") PumpStatus.WATERING else PumpStatus.IDLE,
            timestamp = timestamp,
            isSimulated = isSimulated,
            rawPacket = "$temperature,$humidity,$soilMoisture,$pumpStatus"
        )
    }

    companion object {
        fun fromDomain(data: TelemetryData): TelemetryEntity {
            return TelemetryEntity(
                timestamp = data.timestamp,
                temperature = data.temperature,
                humidity = data.humidity,
                soilMoisture = data.soilMoisture,
                pumpStatus = data.pumpStatus.name,
                isSimulated = data.isSimulated
            )
        }
    }
}

@Entity(tableName = "watering_events")
data class WateringEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val startSoilMoisture: Int,
    val endSoilMoisture: Int
)
