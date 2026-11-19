package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BleConnectionState
import com.example.data.model.SensorQuality
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.CircularGaugeRing
import com.example.ui.components.EcgWaveformCanvas
import com.example.ui.components.MedBandCard
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryLineChart
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState

private enum class TimeWindow { DAILY, WEEKLY, MONTHLY }

@Composable
fun HealthMonitoringScreen(
    viewModel: MedBandViewModel
) {
    val strings = LocalMedBandStrings.current
    val bleState by viewModel.bleManager.connectionState.collectAsStateWithLifecycle()
    val vitals by viewModel.bleManager.liveVitals.collectAsStateWithLifecycle()
    val measurementsState by viewModel.measurementsState.collectAsStateWithLifecycle()
    var selectedWindow by remember { mutableStateOf(TimeWindow.DAILY) }

    val isConnected = bleState == BleConnectionState.CONNECTED
    val quality = if (isConnected) (vitals?.sensorQuality ?: SensorQuality.UNAVAILABLE) else SensorQuality.UNAVAILABLE
    val qualityColor = when (quality) {
        SensorQuality.GOOD -> StatusGreen
        SensorQuality.FAIR -> StatusAmber
        SensorQuality.POOR, SensorQuality.UNAVAILABLE -> EmergencyRed
    }
    val qualityText = when (quality) {
        SensorQuality.GOOD -> strings.qualityGood
        SensorQuality.FAIR -> strings.qualityFair
        SensorQuality.POOR -> strings.qualityPoor
        SensorQuality.UNAVAILABLE -> strings.qualityUnavailable
    }

    val allMeasurements = (measurementsState as? UiState.Success)?.data.orEmpty()
    val nowMs = System.currentTimeMillis()
    val windowMs = when (selectedWindow) {
        TimeWindow.DAILY -> 24L * 3600_000L
        TimeWindow.WEEKLY -> 7L * 24L * 3600_000L
        TimeWindow.MONTHLY -> 30L * 24L * 3600_000L
    }
    val filtered = remember(allMeasurements, selectedWindow) {
        allMeasurements.filter { m ->
            val ts = m.recordedAt?.toDate()?.time ?: nowMs
            nowMs - ts <= windowMs
        }.reversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(strings.healthTitle, style = MaterialTheme.typography.headlineMedium)

            MedBandCard(accentColor = qualityColor.copy(alpha = 0.55f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.liveReadings, style = MaterialTheme.typography.titleMedium)
                    StatusPill(text = "${strings.sensorQuality}: $qualityText", color = qualityColor)
                }

                Spacer(Modifier.height(12.dp))
                if (!isConnected || vitals == null || !vitals!!.fingerDetected) {
                    Text(
                        text = strings.sensorDisconnectedNotice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val liveHr = if (isConnected && vitals?.heartRateBpm != null) vitals!!.heartRateBpm else null
                    CircularGaugeRing(
                        valueText = liveHr?.toString() ?: "--",
                        unitText = strings.bpmUnit,
                        progress = liveHr?.let { it / 200f },
                        ringColor = EmergencyRed,
                        size = 132.dp
                    )

                    val liveSpo2 = if (isConnected && vitals?.spo2Valid == true) vitals?.spo2Percent else null
                    CircularGaugeRing(
                        valueText = liveSpo2?.let { "$it%" } ?: "--",
                        unitText = "SpO2",
                        progress = liveSpo2?.let { it / 100f },
                        ringColor = CyanPulse,
                        size = 132.dp
                    )
                }

                Spacer(Modifier.height(10.dp))
                EcgWaveformCanvas(
                    isLive = isConnected && vitals?.heartRateBpm != null,
                    color = CyanPulse
                )
            }

            // Time Window Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedWindow == TimeWindow.DAILY,
                    onClick = { selectedWindow = TimeWindow.DAILY },
                    label = { Text(strings.viewDaily) },
                    modifier = Modifier.testTag("health_filter_daily")
                )
                FilterChip(
                    selected = selectedWindow == TimeWindow.WEEKLY,
                    onClick = { selectedWindow = TimeWindow.WEEKLY },
                    label = { Text(strings.viewWeekly) },
                    modifier = Modifier.testTag("health_filter_weekly")
                )
                FilterChip(
                    selected = selectedWindow == TimeWindow.MONTHLY,
                    onClick = { selectedWindow = TimeWindow.MONTHLY },
                    label = { Text(strings.viewMonthly) },
                    modifier = Modifier.testTag("health_filter_monthly")
                )
            }

            MedBandCard {
                Text("${strings.heartRate} — ${strings.realTimeChart}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                val hrSeries = filtered.mapNotNull { it.heartRateBpm.takeIf { hr -> hr > 0 }?.toFloat() }
                TelemetryLineChart(
                    values = hrSeries,
                    minY = 40f,
                    maxY = 180f,
                    lineColor = EmergencyRed,
                    emptyLabel = strings.noReadingsYet
                )
            }

            MedBandCard {
                Text("${strings.spo2Title} — ${strings.realTimeChart}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                val spo2Series = filtered.mapNotNull { if (it.spo2Valid) it.spo2Percent?.toFloat() else null }
                TelemetryLineChart(
                    values = spo2Series,
                    minY = 80f,
                    maxY = 100f,
                    lineColor = CyanPulse,
                    emptyLabel = strings.noReadingsYet
                )
            }
        }
    }
}
