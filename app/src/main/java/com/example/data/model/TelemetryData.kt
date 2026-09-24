package com.example.data.model

data class TelemetryData(
    val temperature: Float,
    val humidity: Float,
    val soilMoisture: Int, // 0 to 1023
    val pumpStatus: PumpStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = false,
    val rawPacket: String = ""
) {
    /**
     * Estimated soil moisture percentage.
     * Analog read on Arduino typically yields 0-1023.
     * Note: Depending on soil sensor calibration, lower values often mean dry or vice versa.
     * In the user's Arduino code:
     *   "if (soilValue < SOIL_THRESHOLD) { pumpStatus = 'WATERING'; } else { pumpStatus = 'IDLE'; }"
     * with SOIL_THRESHOLD = 500.
     */
    val soilPercentage: Int
        get() = ((soilMoisture / 1023f) * 100).toInt().coerceIn(0, 100)

    val isDry: Boolean
        get() = soilMoisture < 500

    val soilCategory: SoilCategory
        get() = when {
            soilMoisture < 350 -> SoilCategory.VERY_DRY
            soilMoisture < 500 -> SoilCategory.DRY
            soilMoisture < 750 -> SoilCategory.OPTIMAL
            else -> SoilCategory.VERY_MOIST
        }

    val tempCategory: TempCategory
        get() = when {
            temperature < 18f -> TempCategory.COOL
            temperature <= 28f -> TempCategory.COMFORTABLE
            temperature <= 34f -> TempCategory.WARM
            else -> TempCategory.HOT
        }

    companion object {
        val DEFAULT = TelemetryData(
            temperature = 24.5f,
            humidity = 55.0f,
            soilMoisture = 550,
            pumpStatus = PumpStatus.IDLE,
            isSimulated = true
        )

        /**
         * Parses Arduino telemetry packet format:
         * "Temp,Humidity,Soil,Status"
         * Example: "25.4,62.0,430,WATERING"
         */
        fun parsePacket(line: String, isSimulated: Boolean = false): TelemetryData? {
            return try {
                val clean = line.trim()
                if (clean.isEmpty()) return null
                val parts = clean.split(",")
                if (parts.size < 4) return null

                val temp = parts[0].trim().toFloatOrNull() ?: return null
                val hum = parts[1].trim().toFloatOrNull() ?: return null
                val soil = parts[2].trim().toIntOrNull() ?: return null
                val statusStr = parts[3].trim().uppercase()
                val status = if (statusStr.contains("WATERING")) PumpStatus.WATERING else PumpStatus.IDLE

                TelemetryData(
                    temperature = temp,
                    humidity = hum,
                    soilMoisture = soil,
                    pumpStatus = status,
                    timestamp = System.currentTimeMillis(),
                    isSimulated = isSimulated,
                    rawPacket = clean
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

enum class PumpStatus {
    IDLE,
    WATERING
}

enum class SoilCategory(val label: String) {
    VERY_DRY("Critically Dry"),
    DRY("Dry (Needs Water)"),
    OPTIMAL("Optimal Moisture"),
    VERY_MOIST("Saturated / Moist")
}

enum class TempCategory(val label: String) {
    COOL("Cool"),
    COMFORTABLE("Ideal"),
    WARM("Warm"),
    HOT("High Temperature")
}
