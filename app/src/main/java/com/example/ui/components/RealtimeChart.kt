package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TelemetryData
import com.example.ui.theme.MetricHumidity
import com.example.ui.theme.MetricPumpWatering
import com.example.ui.theme.MetricSoil
import com.example.ui.theme.MetricTemp
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.GraphSeries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RealtimeChartCard(
    dataPoints: List<TelemetryData>,
    selectedSeries: GraphSeries,
    dryThreshold: Int,
    modifier: Modifier = Modifier,
    chartHeight: Int = 220
) {
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("realtime_chart_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Chart Header: Title & Selected Point Tooltip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = selectedSeries.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time stream (${dataPoints.size} samples)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Interactive Scrubber readout pill
                if (selectedPointIndex != null && selectedPointIndex in dataPoints.indices) {
                    val p = dataPoints[selectedPointIndex!!]
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = timeFormat.format(Date(p.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val valueText = when (selectedSeries) {
                                GraphSeries.TEMP -> "${p.temperature}°C"
                                GraphSeries.HUMIDITY -> "${p.humidity}%"
                                GraphSeries.SOIL -> "${p.soilMoisture} u"
                                GraphSeries.ALL -> "T:${p.temperature}° H:${p.humidity}% S:${p.soilMoisture}"
                            }
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // The Canvas Chart
            if (dataPoints.size < 2) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight.dp)
                ) {
                    Text(
                        text = "Collecting incoming telemetry packets…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight.dp)
                ) {
                    RealtimeChartCanvas(
                        dataPoints = dataPoints,
                        selectedSeries = selectedSeries,
                        dryThreshold = dryThreshold,
                        selectedIndex = selectedPointIndex,
                        onPointSelected = { selectedPointIndex = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend Row
            ChartLegendRow(selectedSeries = selectedSeries, dryThreshold = dryThreshold)
        }
    }
}

@Composable
private fun RealtimeChartCanvas(
    dataPoints: List<TelemetryData>,
    selectedSeries: GraphSeries,
    dryThreshold: Int,
    selectedIndex: Int?,
    onPointSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val cursorColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .pointerInput(dataPoints) {
                detectTapGestures(
                    onPress = { offset ->
                        val index = (offset.x / size.width * (dataPoints.size - 1)).toInt()
                            .coerceIn(0, dataPoints.lastIndex)
                        onPointSelected(index)
                    }
                )
            }
            .pointerInput(dataPoints) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val index = (offset.x / size.width * (dataPoints.size - 1)).toInt()
                            .coerceIn(0, dataPoints.lastIndex)
                        onPointSelected(index)
                    },
                    onDragEnd = { onPointSelected(null) },
                    onDragCancel = { onPointSelected(null) },
                    onDrag = { change, _ ->
                        val index = (change.position.x / size.width * (dataPoints.size - 1)).toInt()
                            .coerceIn(0, dataPoints.lastIndex)
                        onPointSelected(index)
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val paddingBottom = 16f
        val paddingTop = 12f
        val chartHeight = height - paddingBottom - paddingTop

        // Draw horizontal grid lines (4 lines)
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = paddingTop + (chartHeight / gridLines) * i
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }

        when (selectedSeries) {
            GraphSeries.TEMP -> {
                val values = dataPoints.map { it.temperature }
                val minVal = (values.minOrNull() ?: 20f) - 2f
                val maxVal = (values.maxOrNull() ?: 35f) + 2f
                drawSingleSeries(
                    values = values,
                    minVal = minVal,
                    maxVal = maxVal,
                    lineColor = MetricTemp,
                    fillColor = MetricTemp.copy(alpha = 0.18f),
                    paddingTop = paddingTop,
                    chartHeight = chartHeight,
                    width = width,
                    selectedIndex = selectedIndex
                )
            }

            GraphSeries.HUMIDITY -> {
                val values = dataPoints.map { it.humidity }
                val minVal = ((values.minOrNull() ?: 40f) - 5f).coerceAtLeast(0f)
                val maxVal = ((values.maxOrNull() ?: 80f) + 5f).coerceAtMost(100f)
                drawSingleSeries(
                    values = values,
                    minVal = minVal,
                    maxVal = maxVal,
                    lineColor = MetricHumidity,
                    fillColor = MetricHumidity.copy(alpha = 0.18f),
                    paddingTop = paddingTop,
                    chartHeight = chartHeight,
                    width = width,
                    selectedIndex = selectedIndex
                )
            }

            GraphSeries.SOIL -> {
                val values = dataPoints.map { it.soilMoisture.toFloat() }
                val minVal = ((values.minOrNull() ?: 300f) - 50f).coerceAtLeast(0f)
                val maxVal = ((values.maxOrNull() ?: 800f) + 50f).coerceAtMost(1023f)

                // Draw Soil Dry Threshold line
                val thresholdRatio = (dryThreshold - minVal) / (maxVal - minVal).coerceAtLeast(1f)
                if (thresholdRatio in 0f..1f) {
                    val threshY = paddingTop + chartHeight * (1f - thresholdRatio)
                    drawLine(
                        color = StatusWarning.copy(alpha = 0.75f),
                        start = Offset(0f, threshY),
                        end = Offset(width, threshY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                    )
                }

                drawSingleSeries(
                    values = values,
                    minVal = minVal,
                    maxVal = maxVal,
                    lineColor = MetricSoil,
                    fillColor = MetricSoil.copy(alpha = 0.18f),
                    paddingTop = paddingTop,
                    chartHeight = chartHeight,
                    width = width,
                    selectedIndex = selectedIndex
                )
            }

            GraphSeries.ALL -> {
                // Multi-series normalized (0..1)
                // Normalize temp (15..35°C), humidity (0..100%), soil (0..1023u)
                val tempValues = dataPoints.map { ((it.temperature - 15f) / 25f).coerceIn(0f, 1f) }
                val humValues = dataPoints.map { (it.humidity / 100f).coerceIn(0f, 1f) }
                val soilValues = dataPoints.map { (it.soilMoisture / 1023f).coerceIn(0f, 1f) }

                // Draw threshold line for soil (500 / 1023)
                val soilThreshNorm = dryThreshold / 1023f
                val threshY = paddingTop + chartHeight * (1f - soilThreshNorm)
                drawLine(
                    color = StatusWarning.copy(alpha = 0.6f),
                    start = Offset(0f, threshY),
                    end = Offset(width, threshY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                )

                // Draw curves
                drawNormalizedLine(
                    values = soilValues,
                    color = MetricSoil,
                    paddingTop = paddingTop,
                    chartHeight = chartHeight,
                    width = width,
                    strokeWidth = 2.5.dp.toPx()
                )
                drawNormalizedLine(
                    values = humValues,
                    color = MetricHumidity,
                    paddingTop = paddingTop,
                    chartHeight = chartHeight,
                    width = width,
                    strokeWidth = 2.dp.toPx()
                )
                drawNormalizedLine(
                    values = tempValues,
                    color = MetricTemp,
                    paddingTop = paddingTop,
                    chartHeight = chartHeight,
                    width = width,
                    strokeWidth = 2.dp.toPx()
                )

                // Draw scrubber cursor
                if (selectedIndex != null && selectedIndex in dataPoints.indices) {
                    val x = (selectedIndex.toFloat() / (dataPoints.size - 1)) * width
                    drawLine(
                        color = cursorColor,
                        start = Offset(x, paddingTop),
                        end = Offset(x, paddingTop + chartHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSingleSeries(
    values: List<Float>,
    minVal: Float,
    maxVal: Float,
    lineColor: Color,
    fillColor: Color,
    paddingTop: Float,
    chartHeight: Float,
    width: Float,
    selectedIndex: Int?
) {
    if (values.size < 2) return
    val span = (maxVal - minVal).coerceAtLeast(0.001f)
    val points = values.mapIndexed { index, v ->
        val x = (index.toFloat() / (values.size - 1)) * width
        val norm = ((v - minVal) / span).coerceIn(0f, 1f)
        val y = paddingTop + chartHeight * (1f - norm)
        Offset(x, y)
    }

    // Build smooth Bezier path
    val strokePath = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cX = (p0.x + p1.x) / 2
            cubicTo(cX, p0.y, cX, p1.y, p1.x, p1.y)
        }
    }

    // Build gradient fill path
    val fillPath = Path().apply {
        addPath(strokePath)
        lineTo(points.last().x, paddingTop + chartHeight)
        lineTo(points.first().x, paddingTop + chartHeight)
        close()
    }

    // Draw gradient fill
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(fillColor, Color.Transparent),
            startY = paddingTop,
            endY = paddingTop + chartHeight
        )
    )

    // Draw main line
    drawPath(
        path = strokePath,
        color = lineColor,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )

    // Draw Scrubber cursor if touched
    if (selectedIndex != null && selectedIndex in points.indices) {
        val selPoint = points[selectedIndex]
        // Vertical dashed line
        drawLine(
            color = lineColor.copy(alpha = 0.8f),
            start = Offset(selPoint.x, paddingTop),
            end = Offset(selPoint.x, paddingTop + chartHeight),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
        // Outer halo
        drawCircle(
            color = lineColor.copy(alpha = 0.25f),
            radius = 8.dp.toPx(),
            center = selPoint
        )
        // Inner dot
        drawCircle(
            color = Color.White,
            radius = 4.5.dp.toPx(),
            center = selPoint
        )
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = selPoint
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNormalizedLine(
    values: List<Float>,
    color: Color,
    paddingTop: Float,
    chartHeight: Float,
    width: Float,
    strokeWidth: Float
) {
    if (values.size < 2) return
    val points = values.mapIndexed { index, v ->
        val x = (index.toFloat() / (values.size - 1)) * width
        val y = paddingTop + chartHeight * (1f - v)
        Offset(x, y)
    }

    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cX = (p0.x + p1.x) / 2
            cubicTo(cX, p0.y, cX, p1.y, p1.x, p1.y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

@Composable
private fun ChartLegendRow(
    selectedSeries: GraphSeries,
    dryThreshold: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (selectedSeries == GraphSeries.ALL) {
            LegendItem(color = MetricTemp, label = "Temp")
            LegendItem(color = MetricHumidity, label = "Humidity")
            LegendItem(color = MetricSoil, label = "Soil")
            LegendItem(color = StatusWarning, label = "Trigger (<$dryThreshold)", isDashed = true)
        } else if (selectedSeries == GraphSeries.SOIL) {
            LegendItem(color = MetricSoil, label = "Moisture (0-1023)")
            LegendItem(color = StatusWarning, label = "Threshold (<$dryThreshold)", isDashed = true)
        } else if (selectedSeries == GraphSeries.TEMP) {
            LegendItem(color = MetricTemp, label = "Temperature (°C)")
        } else if (selectedSeries == GraphSeries.HUMIDITY) {
            LegendItem(color = MetricHumidity, label = "Humidity (%)")
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isDashed: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
