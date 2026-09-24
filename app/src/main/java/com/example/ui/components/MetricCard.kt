package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MetricHumidity
import com.example.ui.theme.MetricSoil
import com.example.ui.theme.MetricTemp
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun TemperatureMetricCard(
    temperature: Float,
    modifier: Modifier = Modifier
) {
    val tempColor by animateColorAsState(
        targetValue = when {
            temperature >= 32f -> StatusDanger
            temperature >= 28f -> StatusWarning
            temperature < 18f -> MetricHumidity
            else -> MetricTemp
        },
        animationSpec = tween(400),
        label = "temp_color"
    )

    val statusText = when {
        temperature >= 32f -> "High Temp"
        temperature >= 28f -> "Warm"
        temperature < 18f -> "Cool"
        else -> "Optimal"
    }

    MetricCard(
        title = "Ambient Temp",
        value = String.format(java.util.Locale.US, "%.1f", temperature),
        unit = "°C",
        statusText = statusText,
        icon = Icons.Rounded.Thermostat,
        accentColor = tempColor,
        progress = ((temperature - 10f) / 30f).coerceIn(0f, 1f),
        subtitle = "DHT11 Pin D9",
        testTag = "metric_temperature",
        modifier = modifier
    )
}

@Composable
fun HumidityMetricCard(
    humidity: Float,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        humidity < 40f -> "Dry Air"
        humidity <= 70f -> "Comfortable"
        else -> "Humid"
    }

    MetricCard(
        title = "Air Humidity",
        value = String.format(java.util.Locale.US, "%.1f", humidity),
        unit = "%",
        statusText = statusText,
        icon = Icons.Rounded.WaterDrop,
        accentColor = MetricHumidity,
        progress = (humidity / 100f).coerceIn(0f, 1f),
        subtitle = "DHT11 Sensor",
        testTag = "metric_humidity",
        modifier = modifier
    )
}

@Composable
fun SoilMoistureMetricCard(
    soilMoisture: Int,
    threshold: Int,
    modifier: Modifier = Modifier
) {
    val isDry = soilMoisture < threshold
    val statusColor = if (isDry) StatusDanger else MetricSoil
    val statusText = if (isDry) "DRY (Triggers Pump)" else "Moist / Healthy"

    // Progress percentage based on 0 to 1023
    val progress = (soilMoisture / 1023f).coerceIn(0f, 1f)

    MetricCard(
        title = "Soil Moisture",
        value = "$soilMoisture",
        unit = "u",
        statusText = statusText,
        icon = Icons.Rounded.Yard,
        accentColor = statusColor,
        progress = progress,
        subtitle = "Threshold: <$threshold",
        testTag = "metric_soil_moisture",
        modifier = modifier
    )
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    statusText: String,
    icon: ImageVector,
    accentColor: Color,
    progress: Float,
    subtitle: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .testTag(testTag)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon, Title & Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Value Row
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Level Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle / Pin Information
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
