package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothConnectionState
import com.example.bluetooth.BluetoothDeviceInfo
import com.example.bluetooth.BluetoothManager
import com.example.data.model.PumpStatus
import com.example.data.model.TelemetryData
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TelemetryUiState(
    val latestTelemetry: TelemetryData = TelemetryData.DEFAULT,
    val recentTelemetryPoints: List<TelemetryData> = emptyList(),
    val rawStringLogs: List<String> = emptyList(),
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val isScanning: Boolean = false,
    val discoveredDevices: List<BluetoothDeviceInfo> = emptyList(),
    val totalWateringCycles: Int = 0,
    val alertMessage: String? = null,
    val lastParsedString: String = "",
    val parseErrorCount: Int = 0
)

/**
 * TelemetryViewModel handles incoming telemetry strings from the FloraPulse
 * Bluetooth device (or simulation), parses the packet format "Temp,Humidity,Soil,Status",
 * updates reactive UI state, and triggers persistence/alerts.
 */
class TelemetryViewModel(
    val bluetoothManager: BluetoothManager,
    private val repository: TelemetryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TelemetryUiState())
    val uiState: StateFlow<TelemetryUiState> = _uiState.asStateFlow()

    init {
        // Collect incoming telemetry strings from BluetoothManager
        viewModelScope.launch {
            bluetoothManager.incomingTelemetryStrings.collect { rawLine ->
                handleIncomingTelemetryString(rawLine)
            }
        }

        // Collect Bluetooth connection state
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connState ->
                _uiState.update { it.copy(connectionState = connState) }
            }
        }

        // Collect Bluetooth scanning state
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
    }

    /**
     * Primary handler for incoming telemetry strings formatted as:
     * "Temp,Humidity,Soil,Status" (e.g., "26.5,58.0,420,WATERING" or "24.2,65.0,610,IDLE").
     *
     * @param telemetryString The raw line received from Arduino via Bluetooth.
     * @return True if parsing succeeded and state updated, false if format was invalid.
     */
    fun handleIncomingTelemetryString(telemetryString: String): Boolean {
        val trimmed = telemetryString.trim()
        if (trimmed.isEmpty()) return false

        val parsedData = TelemetryData.parsePacket(trimmed)
        val timestampStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val loggedLine = "[$timestampStr] $trimmed"

        if (parsedData == null) {
            _uiState.update {
                it.copy(
                    lastParsedString = trimmed,
                    parseErrorCount = it.parseErrorCount + 1,
                    rawStringLogs = (it.rawStringLogs + "$loggedLine [PARSE ERROR]").takeLast(60)
                )
            }
            return false
        }

        val previous = _uiState.value
        val prevPumpStatus = previous.latestTelemetry.pumpStatus

        // Detect pump cycle transitions
        val newCycles = if (prevPumpStatus == PumpStatus.IDLE && parsedData.pumpStatus == PumpStatus.WATERING) {
            previous.totalWateringCycles + 1
        } else {
            previous.totalWateringCycles
        }

        // Check for alerts
        val alert = when {
            parsedData.temperature >= 34.0f -> "High ambient temperature: ${parsedData.temperature}°C!"
            parsedData.soilMoisture < 400 -> "Critically dry soil: ${parsedData.soilMoisture} units!"
            else -> null
        }

        val updatedRecent = (previous.recentTelemetryPoints + parsedData).takeLast(150)

        _uiState.update {
            it.copy(
                latestTelemetry = parsedData,
                recentTelemetryPoints = updatedRecent,
                rawStringLogs = (it.rawStringLogs + loggedLine).takeLast(60),
                totalWateringCycles = newCycles,
                alertMessage = alert,
                lastParsedString = trimmed
            )
        }

        // Persist to Room repository if available
        repository?.let { repo ->
            viewModelScope.launch {
                repo.recordTelemetry(parsedData)
            }
        }

        return true
    }

    fun startScanning() {
        bluetoothManager.startScan()
    }

    fun stopScanning() {
        bluetoothManager.stopScan()
    }

    fun connectToDevice(device: BluetoothDeviceInfo) {
        bluetoothManager.connectToDevice(device, viewModelScope)
    }

    fun connectToFloraPulse(): Boolean {
        return bluetoothManager.connectToFloraPulse(viewModelScope)
    }

    fun disconnect() {
        bluetoothManager.disconnect()
    }

    fun clearLogs() {
        _uiState.update { it.copy(rawStringLogs = emptyList()) }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(alertMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.cleanup()
    }
}

class TelemetryViewModelFactory(
    private val bluetoothManager: BluetoothManager,
    private val repository: TelemetryRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TelemetryViewModel::class.java)) {
            return TelemetryViewModel(bluetoothManager, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
