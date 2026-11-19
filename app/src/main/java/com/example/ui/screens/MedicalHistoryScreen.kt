package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.MedBandCard
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryLineChart
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MedicalHistoryScreen(
    viewModel: MedBandViewModel
) {
    val strings = LocalMedBandStrings.current
    val context = LocalContext.current
    val measurementsState by viewModel.measurementsState.collectAsStateWithLifecycle()
    val rows = (measurementsState as? UiState.Success)?.data.orEmpty()

    val validHr = rows.mapNotNull { it.heartRateBpm.takeIf { hr -> hr > 0 } }
    val avgHr = if (validHr.isNotEmpty()) validHr.average().roundToInt() else null

    val validSpo2 = rows.mapNotNull { if (it.spo2Valid) it.spo2Percent else null }
    val avgSpo2 = if (validSpo2.isNotEmpty()) validSpo2.average().roundToInt() else null

    val hasInvalidSpo2 = rows.any { !it.spo2Valid }
    val dateFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.historyTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val csv = viewModel.buildExportCsvReport()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, "MedBand Health Report")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, strings.exportReport))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.testTag("history_export_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.exportReport, color = Color.White)
                }
            }

            // Daily / Overall Averages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MedBandCard(modifier = Modifier.weight(1f)) {
                    Text(strings.dailyAverageHr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = avgHr?.let { "$it ${strings.bpmUnit}" } ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmergencyRed
                    )
                }
                MedBandCard(modifier = Modifier.weight(1f)) {
                    Text(strings.dailyAverageSpo2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = avgSpo2?.let { "$it%" } ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        color = CyanPulse
                    )
                }
            }

            if (hasInvalidSpo2) {
                MedBandCard(accentColor = StatusAmber.copy(alpha = 0.6f)) {
                    Text(strings.invalidOrMissingData, style = MaterialTheme.typography.bodyMedium, color = StatusAmber)
                }
            }

            MedBandCard {
                Text(strings.heartRate, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TelemetryLineChart(
                    values = validHr.reversed().map { it.toFloat() },
                    minY = 40f,
                    maxY = 180f,
                    lineColor = EmergencyRed,
                    emptyLabel = strings.noReadingsYet
                )
            }

            MedBandCard {
                Text(strings.spo2Title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TelemetryLineChart(
                    values = validSpo2.reversed().map { it.toFloat() },
                    minY = 80f,
                    maxY = 100f,
                    lineColor = CyanPulse,
                    emptyLabel = strings.noReadingsYet
                )
            }

            if (rows.isEmpty()) {
                MedBandCard {
                    Text(strings.noReadingsYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                rows.take(30).forEach { m ->
                    MedBandCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${m.heartRateBpm} ${strings.bpmUnit}  •  SpO2: ${if (m.spo2Valid && m.spo2Percent != null) "${m.spo2Percent}%" else "--"}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = m.recordedAt?.toDate()?.let { dateFmt.format(it) } ?: "--",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(
                                text = m.sensorQuality,
                                color = if (m.sensorQuality == "GOOD") StatusGreen else StatusAmber
                            )
                        }
                    }
                }
            }
        }
    }
}
