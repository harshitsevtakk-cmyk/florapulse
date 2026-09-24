package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TelemetryData
import com.example.ui.components.RealtimeChartCard
import com.example.ui.theme.MetricHumidity
import com.example.ui.theme.MetricSoil
import com.example.ui.theme.MetricTemp
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.GraphSeries
import com.example.ui.viewmodel.TimeWindow

@Composable
fun AnalyticsScreen(
    uiState: DashboardUiState,
    onSelectSeries: (GraphSeries) -> Unit,
    onSelectTimeWindow: (TimeWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Filter points based on selected time window
    val filteredPoints = when (uiState.timeWindow) {
        TimeWindow.LAST_30_SEC -> uiState.recentTelemetryList.takeLast(15)
        TimeWindow.LAST_2_MIN -> uiState.recentTelemetryList.takeLast(60)
        TimeWindow.LAST_5_MIN -> uiState.recentTelemetryList.takeLast(150)
        TimeWindow.ALL -> uiState.recentTelemetryList
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Telemetry Analytics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Real-time dynamic visualization & statistics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Rounded.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        // Series Selector Tabs
        ScrollableTabRow(
            selectedTabIndex = uiState.selectedGraphSeries.ordinal,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            GraphSeries.entries.forEach { series ->
                Tab(
                    selected = uiState.selectedGraphSeries == series,
                    onClick = { onSelectSeries(series) },
                    text = {
                        Text(
                            text = when (series) {
                                GraphSeries.ALL -> "All Signals"
                                GraphSeries.TEMP -> "Temp"
                                GraphSeries.HUMIDITY -> "Humidity"
                                GraphSeries.SOIL -> "Soil Moisture"
                            },
                            fontWeight = if (uiState.selectedGraphSeries == series) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Time window chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TimeWindow.entries.forEach { window ->
                FilterChip(
                    selected = uiState.timeWindow == window,
                    onClick = { onSelectTimeWindow(window) },
                    label = { Text(window.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("time_window_${window.name}")
                )
            }
        }

        // The Main Realtime Chart
        RealtimeChartCard(
            dataPoints = filteredPoints,
            selectedSeries = uiState.selectedGraphSeries,
            dryThreshold = uiState.dryThreshold,
            chartHeight = 260
        )

        // Statistics Summary Cards
        Text(
            text = "STATISTICAL OVERVIEW",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        SensorStatCard(
            title = "Ambient Temperature",
            unit = "°C",
            color = MetricTemp,
            values = filteredPoints.map { it.temperature }
        )

        SensorStatCard(
            title = "Air Humidity",
            unit = "%",
            color = MetricHumidity,
            values = filteredPoints.map { it.humidity }
        )

        SensorStatCard(
            title = "Soil Moisture (Analog 0-1023)",
            unit = "u",
            color = MetricSoil,
            values = filteredPoints.map { it.soilMoisture.toFloat() }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SensorStatCard(
    title: String,
    unit: String,
    color: Color,
    values: List<Float>
) {
    val current = values.lastOrNull() ?: 0f
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 0f
    val avg = if (values.isNotEmpty()) values.average().toFloat() else 0f

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Current: " + String.format(java.util.Locale.US, "%.1f", current) + unit,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatItem(label = "Minimum", value = String.format(java.util.Locale.US, "%.1f", min) + unit)
                StatItem(label = "Average", value = String.format(java.util.Locale.US, "%.1f", avg) + unit)
                StatItem(label = "Maximum", value = String.format(java.util.Locale.US, "%.1f", max) + unit)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
