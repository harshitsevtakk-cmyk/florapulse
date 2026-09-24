package com.example.simulation

import com.example.data.model.PumpStatus
import com.example.data.model.TelemetryData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Realistic FloraPulse hardware & plant environment simulator.
 * Faithfully simulates the Arduino loop:
 * - Telemetry packet sent every 2000 ms: Temp,Humidity,Soil,Status
 * - Dry soil threshold = 500
 * - Soil dries naturally; when < 500, pump triggers WATERING until rehydrated.
 */
class FloraPulseSimulator {

    private val _telemetryFlow = MutableSharedFlow<TelemetryData>(replay = 1)
    val telemetryFlow: SharedFlow<TelemetryData> = _telemetryFlow.asSharedFlow()

    private var simulationJob: Job? = null

    // Simulation state
    private var currentTemp = 24.8f
    private var currentHumidity = 58.2f
    private var currentSoil = 620
    private var pumpStatus = PumpStatus.IDLE
    private var isManualWatering = false

    fun start(scope: CoroutineScope) {
        if (simulationJob?.isActive == true) return
        simulationJob = scope.launch {
            while (isActive) {
                stepSimulation()
                delay(2000L) // Matches Arduino 2000ms interval
            }
        }
    }

    fun stop() {
        simulationJob?.cancel()
        simulationJob = null
    }

    private suspend fun stepSimulation() {
        // Temperature subtle fluctuation (± 0.2°C)
        currentTemp += (Random.nextFloat() - 0.5f) * 0.4f
        currentTemp = currentTemp.coerceIn(21.0f, 32.0f)

        // Humidity subtle drift
        currentHumidity += (Random.nextFloat() - 0.5f) * 0.8f
        currentHumidity = currentHumidity.coerceIn(40.0f, 85.0f)

        // Soil moisture dynamic logic
        val dryThreshold = 500

        if (pumpStatus == PumpStatus.WATERING) {
            // Pump is watering: Soil moisture increases rapidly
            currentSoil += Random.nextInt(45, 75)
            // Once adequately hydrated above threshold + hysteresis (e.g. 680), pump stops
            if (currentSoil >= 680 && !isManualWatering) {
                pumpStatus = PumpStatus.IDLE
            }
        } else {
            // Normal drying: Soil moisture gradually evaporates (-5 to -15 per tick)
            currentSoil -= Random.nextInt(4, 12)
            // Check Arduino logic: if soil < 500, turn pump ON
            if (currentSoil < dryThreshold) {
                pumpStatus = PumpStatus.WATERING
            }
        }

        currentSoil = currentSoil.coerceIn(100, 950)

        // Format packet strictly matching Arduino:
        // String(temperature, 1) + "," + String(humidity, 1) + "," + String(soilValue) + "," + pumpStatus
        val formattedTemp = String.format(java.util.Locale.US, "%.1f", currentTemp)
        val formattedHum = String.format(java.util.Locale.US, "%.1f", currentHumidity)
        val statusStr = if (pumpStatus == PumpStatus.WATERING) "WATERING" else "IDLE"
        val packet = "$formattedTemp,$formattedHum,$currentSoil,$statusStr"

        val telemetry = TelemetryData(
            temperature = formattedTemp.toFloat(),
            humidity = formattedHum.toFloat(),
            soilMoisture = currentSoil,
            pumpStatus = pumpStatus,
            timestamp = System.currentTimeMillis(),
            isSimulated = true,
            rawPacket = packet
        )

        _telemetryFlow.emit(telemetry)
    }

    fun forceWateringToggle() {
        if (pumpStatus == PumpStatus.WATERING) {
            pumpStatus = PumpStatus.IDLE
            isManualWatering = false
        } else {
            pumpStatus = PumpStatus.WATERING
            isManualWatering = true
        }
    }

    fun setSimulatedSoilMoisture(newSoil: Int) {
        currentSoil = newSoil.coerceIn(0, 1023)
    }
}
