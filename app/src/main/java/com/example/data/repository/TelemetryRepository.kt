package com.example.data.repository

import com.example.data.local.TelemetryDao
import com.example.data.local.TelemetryEntity
import com.example.data.local.WateringEventEntity
import com.example.data.model.TelemetryData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TelemetryRepository(private val dao: TelemetryDao) {

    val recentTelemetry: Flow<List<TelemetryData>> = dao.getRecentTelemetry(150).map { list ->
        list.map { it.toDomain() }
    }

    val allTelemetry: Flow<List<TelemetryData>> = dao.getAllTelemetry().map { list ->
        list.map { it.toDomain() }
    }

    val wateringEvents: Flow<List<WateringEventEntity>> = dao.getWateringEvents(50)

    suspend fun recordTelemetry(data: TelemetryData) {
        dao.insertTelemetry(TelemetryEntity.fromDomain(data))
    }

    suspend fun recordWateringEvent(
        startTime: Long,
        endTime: Long,
        startSoil: Int,
        endSoil: Int
    ) {
        val durationSec = ((endTime - startTime) / 1000).toInt().coerceAtLeast(1)
        dao.insertWateringEvent(
            WateringEventEntity(
                startTime = startTime,
                endTime = endTime,
                durationSeconds = durationSec,
                startSoilMoisture = startSoil,
                endSoilMoisture = endSoil
            )
        )
    }

    suspend fun clearAllData() {
        dao.clearAllTelemetry()
        dao.clearAllWateringEvents()
    }
}
