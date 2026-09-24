package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

sealed interface BluetoothConnectionState {
    data object Disconnected : BluetoothConnectionState
    data object Scanning : BluetoothConnectionState
    data class Connecting(val deviceName: String) : BluetoothConnectionState
    data class Connected(val deviceName: String, val address: String) : BluetoothConnectionState
    data class Error(val message: String) : BluetoothConnectionState
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isBonded: Boolean = false
) {
    val isFloraPulseDevice: Boolean
        get() = name.contains("FloraPulse", ignoreCase = true) ||
                name.contains("Flora", ignoreCase = true) ||
                name.contains("HC-05", ignoreCase = true) ||
                name.contains("HC-06", ignoreCase = true)
}

/**
 * BluetoothManager responsible for discovering, scanning, and connecting to the
 * FloraPulse (HC-05 / BT Classic) hardware using Android's BluetoothAdapter.
 * Reads raw incoming telemetry lines and emits them to incomingTelemetryStrings.
 */
class BluetoothManager(private val context: Context) {

    companion object {
        // Standard Serial Port Profile (SPP) UUID for HC-05 / Arduino Bluetooth Classic
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val systemBluetoothManager: SystemBluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? SystemBluetoothManager

    val bluetoothAdapter: BluetoothAdapter? = systemBluetoothManager?.adapter

    private val _connectionState =
        MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _incomingTelemetryStrings = MutableSharedFlow<String>(replay = 1)
    val incomingTelemetryStrings: SharedFlow<String> = _incomingTelemetryStrings.asSharedFlow()

    private var activeSocket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    private var isReceiverRegistered = false

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // BroadcastReceiver for Bluetooth device discovery
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        val deviceName = try {
                            device.name ?: "Unknown Bluetooth Device"
                        } catch (_: SecurityException) {
                            "Unknown Bluetooth Device"
                        }
                        val info = BluetoothDeviceInfo(
                            name = deviceName,
                            address = device.address,
                            isBonded = device.bondState == BluetoothDevice.BOND_BONDED
                        )

                        val currentList = _discoveredDevices.value
                        if (currentList.none { it.address == info.address }) {
                            _discoveredDevices.value = currentList + info
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    /**
     * Retrieves all bonded (paired) Bluetooth devices from BluetoothAdapter.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        if (!isBluetoothSupported || !isBluetoothEnabled) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.map {
                BluetoothDeviceInfo(
                    name = it.name ?: "Unknown Device",
                    address = it.address,
                    isBonded = true
                )
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Starts scanning / discovering nearby Bluetooth devices using BluetoothAdapter.startDiscovery().
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth is disabled or unavailable")
            return
        }

        try {
            registerReceiver()
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            // Seed discovered devices with paired devices first
            _discoveredDevices.value = getPairedDevices()
            val started = adapter.startDiscovery()
            _isScanning.value = started
            if (started && _connectionState.value is BluetoothConnectionState.Disconnected) {
                _connectionState.value = BluetoothConnectionState.Scanning
            }
        } catch (e: SecurityException) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth scan permission missing")
            _isScanning.value = false
        }
    }

    /**
     * Stops Bluetooth discovery.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (_: SecurityException) {
        } finally {
            _isScanning.value = false
            if (_connectionState.value is BluetoothConnectionState.Scanning) {
                _connectionState.value = BluetoothConnectionState.Disconnected
            }
        }
    }

    /**
     * Connects to a target FloraPulse device (or selected BluetoothDeviceInfo) via SPP RFCOMM.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(
        deviceInfo: BluetoothDeviceInfo,
        scope: CoroutineScope
    ) {
        stopScan()
        disconnect()

        connectionJob = scope.launch(Dispatchers.IO) {
            _connectionState.value = BluetoothConnectionState.Connecting(deviceInfo.name)
            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                _connectionState.value = BluetoothConnectionState.Error("Bluetooth is disabled")
                return@launch
            }

            try {
                // Discovery must be canceled prior to connecting
                adapter.cancelDiscovery()
                val remoteDevice: BluetoothDevice = adapter.getRemoteDevice(deviceInfo.address)
                val socket = remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                activeSocket = socket
                socket.connect()

                _connectionState.value = BluetoothConnectionState.Connected(
                    deviceName = deviceInfo.name,
                    address = deviceInfo.address
                )

                // Read incoming telemetry strings continuously
                val inputStream = socket.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))

                while (isActive && socket.isConnected) {
                    val line = try {
                        reader.readLine()
                    } catch (e: IOException) {
                        if (isActive) {
                            _connectionState.value =
                                BluetoothConnectionState.Error("Connection disconnected: ${e.localizedMessage}")
                        }
                        break
                    }

                    if (line != null && line.isNotBlank()) {
                        _incomingTelemetryStrings.emit(line.trim())
                    }
                }
            } catch (e: SecurityException) {
                _connectionState.value = BluetoothConnectionState.Error("Bluetooth permission denied")
            } catch (e: Exception) {
                _connectionState.value =
                    BluetoothConnectionState.Error("Failed to connect to ${deviceInfo.name}: ${e.localizedMessage ?: "Device unreachable"}")
            } finally {
                cleanupSocket()
            }
        }
    }

    /**
     * Auto-connects to the first available FloraPulse device from paired or discovered devices.
     */
    fun connectToFloraPulse(scope: CoroutineScope): Boolean {
        val floraDevice = getPairedDevices().firstOrNull { it.isFloraPulseDevice }
            ?: _discoveredDevices.value.firstOrNull { it.isFloraPulseDevice }

        return if (floraDevice != null) {
            connectToDevice(floraDevice, scope)
            true
        } else {
            startScan()
            false
        }
    }

    /**
     * Disconnects the active Bluetooth socket and resets connection state.
     */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        cleanupSocket()
        _connectionState.value = BluetoothConnectionState.Disconnected
    }

    private fun cleanupSocket() {
        try {
            activeSocket?.close()
        } catch (_: IOException) {
        } finally {
            activeSocket = null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        val socket = activeSocket
        if (socket == null || !socket.isConnected) return@withContext false
        try {
            val outputStream = socket.outputStream
            outputStream.write((command + "\n").toByteArray())
            outputStream.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            try {
                context.registerReceiver(discoveryReceiver, filter)
                isReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
    }

    fun cleanup() {
        disconnect()
        stopScan()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (_: Exception) {
            } finally {
                isReceiverRegistered = false
            }
        }
    }
}
