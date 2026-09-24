package com.example.ui.viewmodel

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothConnectionState
import com.example.bluetooth.BluetoothDeviceInfo
import com.example.bluetooth.BluetoothManager
import com.example.data.local.WateringEventEntity
import com.example.data.model.PumpStatus
import com.example.data.model.TelemetryData
import com.example.data.repository.TelemetryRepository
import com.example.simulation.FloraPulseSimulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GraphSeries(val title: String) {
    ALL("Overview"),
    TEMP("Temperature (°C)"),
    HUMIDITY("Air Humidity (%)"),
    SOIL("Soil Moisture (0-1023)")
}

enum class TimeWindow(val label: String, val pointCount: Int) {
    LAST_30_SEC("30s", 15),
    LAST_2_MIN("2m", 60),
    LAST_5_MIN("5m", 150),
    ALL("All", Int.MAX_VALUE)
}

data class DashboardUiState(
    val latestTelemetry: TelemetryData = TelemetryData.DEFAULT,
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val isScanning: Boolean = false,
    val discoveredDevices: List<BluetoothDeviceInfo> = emptyList(),
    val isSimulationActive: Boolean = true, // Default to true so emulators & initial launch immediately see live flowing telemetry!
    val recentTelemetryList: List<TelemetryData> = emptyList(),
    val rawConsoleLogs: List<String> = emptyList(),
    val dryThreshold: Int = 500, // Matches Arduino SOIL_THRESHOLD = 500
    val highTempThreshold: Float = 34.0f,
    val selectedGraphSeries: GraphSeries = GraphSeries.ALL,
    val timeWindow: TimeWindow = TimeWindow.LAST_2_MIN,
    val totalWateringCycles: Int = 0,
    val lastWateringDurationSec: Int = 0,
    val activeAlert: String? = null,
    val isAlertDismissed: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val isScrubbingGraph: Boolean = false,
    val scrubbedPoint: TelemetryData? = null
)

class DashboardViewModel(
    private val repository: TelemetryRepository,
    val bluetoothManager: BluetoothManager,
    private val context: Context
) : ViewModel() {

    private val simulator = FloraPulseSimulator()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Persistent Room historical telemetry flow
    val persistentTelemetry: StateFlow<List<TelemetryData>> = repository.allTelemetry
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Persistent Room watering events flow
    val wateringEvents: StateFlow<List<WateringEventEntity>> = repository.wateringEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Tracking active watering event state
    private var wateringStartTime: Long? = null
    private var wateringStartSoil: Int = 0

    init {
        // Collect real incoming Bluetooth telemetry strings
        viewModelScope.launch {
            bluetoothManager.incomingTelemetryStrings.collect { rawString ->
                if (!_uiState.value.isSimulationActive) {
                    handleIncomingTelemetryString(rawString)
                }
            }
        }

        // Collect Bluetooth connection state
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is BluetoothConnectionState.Connected) {
                    // When real device connects, pause simulation automatically
                    setSimulationActive(false)
                }
            }
        }

        // Collect Bluetooth discovery/scanning state
        viewModelScope.launch {
            bluetoothManager.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }

        // Collect discovered devices
        viewModelScope.launch {
            bluetoothManager.discoveredDevices.collect { devices ->
                _uiState.update { it.copy(discoveredDevices = devices) }
            }
        }

        // Collect simulation telemetry
        viewModelScope.launch {
            simulator.telemetryFlow.collect { data ->
                if (_uiState.value.isSimulationActive) {
                    handleIncomingTelemetryString(data.rawPacket, isSimulated = true)
                }
            }
        }

        // Start simulator by default
        simulator.start(viewModelScope)
    }

    /**
     * Handles incoming telemetry strings formatted as:
     * "Temp,Humidity,Soil,Status" (e.g., "26.5,58.0,420,WATERING" or "24.2,65.0,610,IDLE").
     */
    fun handleIncomingTelemetryString(rawString: String, isSimulated: Boolean = false): Boolean {
        val trimmed = rawString.trim()
        if (trimmed.isEmpty()) return false

        appendRawConsoleLog(trimmed)

        val parsedData = TelemetryData.parsePacket(trimmed, isSimulated = isSimulated) ?: return false
        processIncomingTelemetry(parsedData)
        return true
    }

    private fun processIncomingTelemetry(data: TelemetryData) {
        val previousState = _uiState.value
        val prevPumpStatus = previousState.latestTelemetry.pumpStatus

        // Detect pump state transitions to record watering event
        if (prevPumpStatus == PumpStatus.IDLE && data.pumpStatus == PumpStatus.WATERING) {
            // Pump turned ON
            wateringStartTime = System.currentTimeMillis()
            wateringStartSoil = data.soilMoisture
            triggerVibration()
        } else if (prevPumpStatus == PumpStatus.WATERING && data.pumpStatus == PumpStatus.IDLE) {
            // Pump turned OFF
            val startTime = wateringStartTime ?: (System.currentTimeMillis() - 2000L)
            val endTime = System.currentTimeMillis()
            val startSoil = wateringStartSoil
            val endSoil = data.soilMoisture

            viewModelScope.launch {
                repository.recordWateringEvent(startTime, endTime, startSoil, endSoil)
            }
            wateringStartTime = null
        }

        // Check for alerts
        var alertMessage: String? = null
        if (data.temperature >= previousState.highTempThreshold) {
            alertMessage = "High temperature detected (${data.temperature}°C)! Ensure ventilation."
        } else if (data.soilMoisture < (previousState.dryThreshold - 150)) {
            alertMessage = "Soil critically dry (${data.soilMoisture} units)! Automated pump engaged."
        }

        // Maintain in-memory sliding window for the real-time graph
        val updatedRecent = (previousState.recentTelemetryList + data).takeLast(180)

        val updatedCycles = if (data.pumpStatus == PumpStatus.WATERING && prevPumpStatus == PumpStatus.IDLE) {
            previousState.totalWateringCycles + 1
        } else {
            previousState.totalWateringCycles
        }

        _uiState.update {
            it.copy(
                latestTelemetry = data,
                recentTelemetryList = updatedRecent,
                totalWateringCycles = updatedCycles,
                activeAlert = if (it.isAlertDismissed) null else alertMessage
            )
        }

        // Persist to Room database
        viewModelScope.launch {
            repository.recordTelemetry(data)
        }
    }

    private fun appendRawConsoleLog(line: String) {
        val timestampStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val formatted = "[$timestampStr] $line"
        _uiState.update {
            val nextList = (it.rawConsoleLogs + formatted).takeLast(50)
            it.copy(rawConsoleLogs = nextList)
        }
    }

    fun setSimulationActive(active: Boolean) {
        if (active) {
            bluetoothManager.disconnect()
            simulator.start(viewModelScope)
        } else {
            simulator.stop()
        }
        _uiState.update { it.copy(isSimulationActive = active) }
    }

    fun forceWateringToggle() {
        if (_uiState.value.isSimulationActive) {
            simulator.forceWateringToggle()
        } else {
            viewModelScope.launch {
                // Send manual override command over Bluetooth serial
                val command = if (_uiState.value.latestTelemetry.pumpStatus == PumpStatus.WATERING) "PUMP:OFF" else "PUMP:ON"
                bluetoothManager.sendCommand(command)
            }
        }
    }

    fun setSimulatedSoilMoisture(value: Int) {
        simulator.setSimulatedSoilMoisture(value)
    }

    fun connectToDevice(deviceInfo: BluetoothDeviceInfo) {
        setSimulationActive(false)
        bluetoothManager.connectToDevice(deviceInfo, viewModelScope)
    }

    fun connectToFloraPulse(): Boolean {
        setSimulationActive(false)
        return bluetoothManager.connectToFloraPulse(viewModelScope)
    }

    fun startScanning() {
        bluetoothManager.startScan()
    }

    fun stopScanning() {
        bluetoothManager.stopScan()
    }

    fun disconnectBluetooth() {
        bluetoothManager.disconnect()
    }

    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        return bluetoothManager.getPairedDevices()
    }

    fun updateDryThreshold(newThreshold: Int) {
        _uiState.update { it.copy(dryThreshold = newThreshold) }
    }

    fun updateHighTempThreshold(newThreshold: Float) {
        _uiState.update { it.copy(highTempThreshold = newThreshold) }
    }

    fun setSelectedGraphSeries(series: GraphSeries) {
        _uiState.update { it.copy(selectedGraphSeries = series) }
    }

    fun setTimeWindow(window: TimeWindow) {
        _uiState.update { it.copy(timeWindow = window) }
    }

    fun setScrubbedPoint(point: TelemetryData?) {
        _uiState.update { it.copy(isScrubbingGraph = point != null, scrubbedPoint = point) }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(activeAlert = null, isAlertDismissed = true) }
    }

    fun toggleVibration(enabled: Boolean) {
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllData()
            _uiState.update {
                it.copy(
                    recentTelemetryList = listOf(it.latestTelemetry),
                    rawConsoleLogs = emptyList()
                )
            }
        }
    }

    private fun triggerVibration() {
        if (!_uiState.value.vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(180)
            }
        } catch (_: Exception) {
        }
    }

    fun exportCsvString(): String {
        val readings = _uiState.value.recentTelemetryList
        val sb = StringBuilder()
        sb.append("Timestamp,DateTime,Temperature_C,Humidity_Percent,SoilMoisture_Raw,PumpStatus,IsSimulated\n")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        for (item in readings) {
            val dateStr = sdf.format(java.util.Date(item.timestamp))
            sb.append("${item.timestamp},$dateStr,${item.temperature},${item.humidity},${item.soilMoisture},${item.pumpStatus.name},${item.isSimulated}\n")
        }
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        simulator.stop()
        bluetoothManager.cleanup()
    }
}

class DashboardViewModelFactory(
    private val repository: TelemetryRepository,
    private val bluetoothManager: BluetoothManager,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(repository, bluetoothManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
