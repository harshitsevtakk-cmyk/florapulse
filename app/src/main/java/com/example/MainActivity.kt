package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluetooth.BluetoothDeviceInfo
import com.example.bluetooth.BluetoothManager
import com.example.ui.components.BluetoothDeviceDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FloraPulseTheme
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.DashboardViewModelFactory
import kotlinx.coroutines.launch

enum class ScreenTab(val title: String, val icon: ImageVector, val tag: String) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard, "tab_dashboard"),
    ANALYTICS("Graphs", Icons.Rounded.Assessment, "tab_analytics"),
    HISTORY("Logs", Icons.Rounded.History, "tab_history"),
    SETTINGS("Settings", Icons.Rounded.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FloraPulseApp
        val bluetoothManager = BluetoothManager(this)

        setContent {
            FloraPulseTheme {
                val viewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModelFactory(
                        repository = app.repository,
                        bluetoothManager = bluetoothManager,
                        context = applicationContext
                    )
                )

                FloraPulseAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloraPulseAppScreen(viewModel: DashboardViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wateringEvents by viewModel.wateringEvents.collectAsStateWithLifecycle()
    val persistentHistory by viewModel.persistentTelemetry.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(ScreenTab.DASHBOARD) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Bluetooth permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showDeviceDialog = true
            viewModel.startScanning()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Bluetooth permission is required to scan and connect to FloraPulse.")
            }
        }
    }

    val requestBluetoothAndShowDialog = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (hasConnect && hasScan) {
                showDeviceDialog = true
                viewModel.startScanning()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        } else {
            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) {
                showDeviceDialog = true
                viewModel.startScanning()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "FloraPulse",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                ScreenTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                label = "screen_crossfade"
            ) { tab ->
                when (tab) {
                    ScreenTab.DASHBOARD -> {
                        DashboardScreen(
                            uiState = uiState,
                            onOpenBluetoothDialog = requestBluetoothAndShowDialog,
                            onDisconnectBluetooth = { viewModel.disconnectBluetooth() },
                            onToggleSimulation = { viewModel.setSimulationActive(it) },
                            onDismissAlert = { viewModel.dismissAlert() },
                            onForceWaterToggle = { viewModel.forceWateringToggle() },
                            onSimulateDrySoil = { viewModel.setSimulatedSoilMoisture(380) },
                            onSimulateMoistSoil = { viewModel.setSimulatedSoilMoisture(720) }
                        )
                    }

                    ScreenTab.ANALYTICS -> {
                        AnalyticsScreen(
                            uiState = uiState,
                            onSelectSeries = { viewModel.setSelectedGraphSeries(it) },
                            onSelectTimeWindow = { viewModel.setTimeWindow(it) }
                        )
                    }

                    ScreenTab.HISTORY -> {
                        HistoryScreen(
                            packetLogs = uiState.rawConsoleLogs,
                            recentTelemetry = persistentHistory.ifEmpty { uiState.recentTelemetryList },
                            wateringEvents = wateringEvents,
                            onClearLogs = { viewModel.clearHistory() },
                            onClearAllData = {
                                viewModel.clearHistory()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("History and database records cleared.")
                                }
                            },
                            onExportCsv = { viewModel.exportCsvString() }
                        )
                    }

                    ScreenTab.SETTINGS -> {
                        SettingsScreen(
                            uiState = uiState,
                            onDryThresholdChange = { viewModel.updateDryThreshold(it) },
                            onHighTempThresholdChange = { viewModel.updateHighTempThreshold(it) },
                            onToggleVibration = { viewModel.toggleVibration(it) },
                            onToggleSimulation = { viewModel.setSimulationActive(it) },
                            onOpenBluetoothDialog = requestBluetoothAndShowDialog,
                            onClearDatabase = {
                                viewModel.clearHistory()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Database records wiped.")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Bluetooth Device Selection Dialog
    if (showDeviceDialog) {
        val pairedList = remember { viewModel.getPairedDevices() }
        BluetoothDeviceDialog(
            pairedDevices = pairedList,
            discoveredDevices = uiState.discoveredDevices,
            isScanning = uiState.isScanning,
            onStartScan = { viewModel.startScanning() },
            onStopScan = { viewModel.stopScanning() },
            onSelectDevice = { device ->
                viewModel.connectToDevice(device)
                showDeviceDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Connecting to ${device.name}…")
                }
            },
            onStartSimulation = {
                viewModel.setSimulationActive(true)
                showDeviceDialog = false
            },
            onDismiss = {
                viewModel.stopScanning()
                showDeviceDialog = false
            }
        )
    }
}
