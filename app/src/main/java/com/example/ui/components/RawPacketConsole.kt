package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MetricSoil

@Composable
fun RawPacketConsole(
    packetLogs: List<String>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 180
) {
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(packetLogs.size) {
        if (autoScroll && packetLogs.isNotEmpty()) {
            listState.animateScrollToItem(packetLogs.lastIndex)
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101712)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("raw_packet_console")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = "Serial Console",
                        tint = MetricSoil,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Serial Stream (9600 baud)",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE1E8E2),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Auto-scroll",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (autoScroll) MetricSoil else Color.Gray,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(
                        onClick = { autoScroll = !autoScroll },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (autoScroll) MetricSoil else Color.Gray,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }

                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ClearAll,
                            contentDescription = "Clear logs",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = "Format: Temp(°C), Humidity(%), Soil(0-1023), Status",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF819385),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Log stream list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxHeightDp.dp)
                    .background(Color(0xFF0A0F0B), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (packetLogs.isEmpty()) {
                    Text(
                        text = "Awaiting serial packets from HC-05…",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(packetLogs) { logLine ->
                            Text(
                                text = logLine,
                                color = if (logLine.contains("WATERING")) Color(0xFF00E5FF) else Color(0xFF98E8B6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
