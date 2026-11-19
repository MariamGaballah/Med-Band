package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BleConnectionState
import com.example.data.model.FallStage
import com.example.data.model.MovementActivity
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.MedBandCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun MovementMonitoringScreen(
    viewModel: MedBandViewModel
) {
    val strings = LocalMedBandStrings.current
    val bleState by viewModel.bleManager.connectionState.collectAsStateWithLifecycle()
    val motion by viewModel.bleManager.liveMotion.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val emergenciesState by viewModel.emergenciesState.collectAsStateWithLifecycle()
    val pendingCountdown by viewModel.pendingFallCountdownSec.collectAsStateWithLifecycle()

    val pendingFall = (emergenciesState as? UiState.Success)?.data
        ?.firstOrNull { it.status == "PENDING_CONFIRMATION" && it.triggerType == "POSSIBLE_FALL" }

    val isConnected = bleState == BleConnectionState.CONNECTED

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
            Text(strings.movementTitle, style = MaterialTheme.typography.headlineMedium)

            // Experimental Disclaimer Banner
            MedBandCard(accentColor = StatusAmber.copy(alpha = 0.65f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = StatusAmber)
                    Text(strings.experimentalFallBanner, style = MaterialTheme.typography.bodyMedium, color = StatusAmber)
                }
            }

            // Pending Fall Confirmation Card
            if (pendingFall != null) {
                MedBandCard(accentColor = EmergencyRed, containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed)
                        Text(
                            text = "${strings.possibleFallDetected} (${pendingCountdown ?: 0}s)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.confirmEmergency(pendingFall.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                            modifier = Modifier.weight(1f).testTag("movement_confirm_fall_button")
                        ) {
                            Text(strings.confirmFall, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = { viewModel.cancelEmergency(pendingFall.id) },
                            modifier = Modifier.weight(1f).testTag("movement_dismiss_fall_button")
                        ) {
                            Text(strings.dismissFall)
                        }
                    }
                }
            }

            // Live Activity & Multi-Stage Fall Status
            MedBandCard(accentColor = CyanPulse.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = CyanPulse)
                        Text(strings.activityLabel, style = MaterialTheme.typography.titleMedium)
                    }
                    val actLabel = if (!isConnected || motion == null) strings.qualityUnavailable else when (motion!!.activity) {
                        MovementActivity.RESTING -> strings.resting
                        MovementActivity.WALKING -> strings.walking
                        MovementActivity.ACTIVE -> strings.active
                        MovementActivity.UNUSUAL_MOVEMENT -> strings.unusualMovement
                        MovementActivity.POSSIBLE_FALL -> strings.possibleFall
                    }
                    val actColor = when (motion?.activity) {
                        MovementActivity.POSSIBLE_FALL -> EmergencyRed
                        MovementActivity.UNUSUAL_MOVEMENT -> StatusAmber
                        else -> StatusGreen
                    }
                    StatusPill(text = actLabel, color = if (isConnected) actColor else MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Fall Stage: ${if (isConnected) (motion?.fallStage ?: FallStage.NONE) else "UNAVAILABLE"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanPulse
                )
            }

            // Accelerometer & Gyroscope Readings
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MedBandCard(modifier = Modifier.weight(1f)) {
                    Text(strings.accelerometerLabel, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (isConnected && motion != null) {
                        val m = motion!!
                        Text("X: ${String.format(Locale.US, "%.2f", m.accelX)} m/s²", style = MaterialTheme.typography.labelMedium)
                        Text("Y: ${String.format(Locale.US, "%.2f", m.accelY)} m/s²", style = MaterialTheme.typography.labelMedium)
                        Text("Z: ${String.format(Locale.US, "%.2f", m.accelZ)} m/s²", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("|a|: ${String.format(Locale.US, "%.2f", m.accelMagnitudeG)} g", style = MaterialTheme.typography.titleMedium, color = CyanPulse)
                    } else {
                        Text("--", style = MaterialTheme.typography.displayMedium)
                    }
                }

                MedBandCard(modifier = Modifier.weight(1f)) {
                    Text(strings.gyroscopeLabel, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (isConnected && motion != null) {
                        val m = motion!!
                        Text("X: ${String.format(Locale.US, "%.1f", m.gyroX)} °/s", style = MaterialTheme.typography.labelMedium)
                        Text("Y: ${String.format(Locale.US, "%.1f", m.gyroY)} °/s", style = MaterialTheme.typography.labelMedium)
                        Text("Z: ${String.format(Locale.US, "%.1f", m.gyroZ)} °/s", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("|ω|: ${String.format(Locale.US, "%.1f", m.gyroMagnitudeDps)} °/s", style = MaterialTheme.typography.titleMedium, color = CyanPulse)
                    } else {
                        Text("--", style = MaterialTheme.typography.displayMedium)
                    }
                }
            }

            // Experimental Sensitivity Config
            MedBandCard {
                Text(strings.sensitivityLabel, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CONSERVATIVE", "BALANCED", "SENSITIVE").forEach { mode ->
                        FilterChip(
                            selected = prefs.fallSensitivity == mode,
                            onClick = { viewModel.setFallSensitivity(mode) },
                            label = { Text(mode) },
                            modifier = Modifier.testTag("movement_sensitivity_$mode")
                        )
                    }
                }
            }
        }
    }
}
