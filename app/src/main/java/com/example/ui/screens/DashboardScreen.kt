package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.PumpStatus
import com.example.ui.components.ConnectionStatusBar
import com.example.ui.components.HumidityMetricCard
import com.example.ui.components.PumpStatusCard
import com.example.ui.components.RealtimeChartCard
import com.example.ui.components.SoilMoistureMetricCard
import com.example.ui.components.TemperatureMetricCard
import com.example.ui.theme.MetricPumpWatering
import com.example.ui.theme.MetricSoil
import com.example.ui.theme.StatusDanger
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.GraphSeries

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onOpenBluetoothDialog: () -> Unit,
    onDisconnectBluetooth: () -> Unit,
    onToggleSimulation: (Boolean) -> Unit,
    onDismissAlert: () -> Unit,
    onForceWaterToggle: () -> Unit,
    onSimulateDrySoil: () -> Unit,
    onSimulateMoistSoil: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Visual Banner
        HeroDashboardBanner(
            soilPercentage = uiState.latestTelemetry.soilPercentage,
            pumpStatus = uiState.latestTelemetry.pumpStatus,
            isDry = uiState.latestTelemetry.isDry
        )

        // Connection & Alert status
        ConnectionStatusBar(
            connectionState = uiState.connectionState,
            isSimulationActive = uiState.isSimulationActive,
            activeAlert = uiState.activeAlert,
            onOpenBluetoothDialog = onOpenBluetoothDialog,
            onDisconnect = onDisconnectBluetooth,
            onToggleSimulation = onToggleSimulation,
            onDismissAlert = onDismissAlert
        )

        // Sensor Metric Cards (2x2 Grid or Staggered Row/Column)
        Text(
            text = "LIVE SENSOR TELEMETRY",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TemperatureMetricCard(
                temperature = uiState.latestTelemetry.temperature,
                modifier = Modifier.weight(1f)
            )
            HumidityMetricCard(
                humidity = uiState.latestTelemetry.humidity,
                modifier = Modifier.weight(1f)
            )
        }

        SoilMoistureMetricCard(
            soilMoisture = uiState.latestTelemetry.soilMoisture,
            threshold = uiState.dryThreshold
        )

        // Water Pump Actuator State Card
        PumpStatusCard(
            pumpStatus = uiState.latestTelemetry.pumpStatus,
            soilMoisture = uiState.latestTelemetry.soilMoisture,
            soilThreshold = uiState.dryThreshold,
            totalCycles = uiState.totalWateringCycles,
            onForceWaterToggle = onForceWaterToggle
        )

        // Real-time chart preview
        RealtimeChartCard(
            dataPoints = uiState.recentTelemetryList.takeLast(35),
            selectedSeries = GraphSeries.ALL,
            dryThreshold = uiState.dryThreshold,
            chartHeight = 180
        )

        // Quick Simulation Controls (if simulation is active)
        if (uiState.isSimulationActive) {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Simulation Playground",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Test Arduino Logic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onSimulateDrySoil,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_dry_soil_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WbSunny,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Drop to 380u (Dry)", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = onSimulateMoistSoil,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_moist_soil_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Opacity,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset to 720u (Wet)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HeroDashboardBanner(
    soilPercentage: Int,
    pumpStatus: PumpStatus,
    isDry: Boolean
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_banner_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            // Background hero image
            Image(
                painter = painterResource(id = R.drawable.florapulse_banner),
                contentDescription = "FloraPulse Hero Graphic",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            )

            // Banner text content
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "FloraPulse System",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Autonomous Irrigation & Environmental Telemetry",
                            color = Color(0xFFD2E8D4),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // System status pill
                    Surface(
                        color = if (pumpStatus == PumpStatus.WATERING) MetricPumpWatering else if (isDry) StatusDanger else MetricSoil,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (pumpStatus == PumpStatus.WATERING) "WATERING ACTIVE" else if (isDry) "SOIL DRY" else "HEALTHY",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Plant Hydration bar
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Soil Hydration Level",
                            color = Color(0xFFE1E8E2),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "$soilPercentage%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { soilPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isDry) Color(0xFFFFB300) else Color(0xFF00E5FF),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
