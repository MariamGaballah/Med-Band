package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BleConnectionState
import com.example.data.model.MovementActivity
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.AnimatedConnectionBadge
import com.example.ui.components.CircularGaugeRing
import com.example.ui.components.EcgWaveformCanvas
import com.example.ui.components.MedBandCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    viewModel: MedBandViewModel,
    userDisplayName: String,
    onNavigateBluetooth: () -> Unit,
    onNavigateNfc: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateEmergency: () -> Unit,
    onNavigateMovement: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateHealth: () -> Unit
) {
    val strings = LocalMedBandStrings.current
    val bleState by viewModel.bleManager.connectionState.collectAsStateWithLifecycle()
    val vitals by viewModel.bleManager.liveVitals.collectAsStateWithLifecycle()
    val motion by viewModel.bleManager.liveMotion.collectAsStateWithLifecycle()
    val health by viewModel.bleManager.deviceHealth.collectAsStateWithLifecycle()
    val lastSyncMs by viewModel.bleManager.lastSyncMillis.collectAsStateWithLifecycle()
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
    val emergenciesState by viewModel.emergenciesState.collectAsStateWithLifecycle()

    val unreadAlerts = (alertsState as? UiState.Success)?.data?.count { !it.acknowledged } ?: 0
    val activeEmergency = (emergenciesState as? UiState.Success)?.data
        ?.any { it.status == "CONFIRMED" || it.status == "PENDING_CONFIRMATION" } == true

    val isConnected = bleState == BleConnectionState.CONNECTED
    val connLabel = when (bleState) {
        BleConnectionState.CONNECTED -> strings.braceletConnected
        BleConnectionState.CONNECTING, BleConnectionState.DISCOVERING_SERVICES, BleConnectionState.SCANNING -> strings.braceletConnecting
        BleConnectionState.DISCONNECTED, BleConnectionState.ERROR -> strings.braceletDisconnected
    }
    val connColor = when (bleState) {
        BleConnectionState.CONNECTED -> StatusGreen
        BleConnectionState.CONNECTING, BleConnectionState.DISCOVERING_SERVICES, BleConnectionState.SCANNING -> StatusAmber
        BleConnectionState.DISCONNECTED, BleConnectionState.ERROR -> EmergencyRed
    }

    val battery = health?.batteryPercent ?: vitals?.batteryPercent
    val syncText = lastSyncMs?.let {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: strings.neverSynced

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Brand & Notification Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = CyanPulse
                    )
                    Text(
                        text = "${strings.welcomeBack}, $userDisplayName",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onNavigateNotifications,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadAlerts > 0) {
                                Badge(containerColor = EmergencyRed) {
                                    Text(unreadAlerts.coerceAtMost(99).toString(), color = Color.White)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = strings.navAlerts,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Hero Bracelet & Connection Card
            MedBandCard(
                accentColor = connColor.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedConnectionBadge(
                            label = connLabel,
                            color = connColor,
                            isPulsing = isConnected || bleState == BleConnectionState.CONNECTING
                        )
                        Text(
                            text = "${strings.lastSync}: $syncText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = strings.batteryLevel,
                                tint = if ((battery ?: 100) <= 15) EmergencyRed else CyanPulse,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${strings.batteryLevel}: ${battery?.let { "$it%" } ?: strings.batteryUnavailable}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.size(10.dp))
                    Image(
                        painter = painterResource(id = R.drawable.img_medband_bracelet),
                        contentDescription = strings.appName,
                        modifier = Modifier
                            .size(94.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isConnected) {
                        Button(
                            onClick = onNavigateBluetooth,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_connect_button")
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.size(6.dp))
                            Text(strings.connectBracelet, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.bleManager.disconnect() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_disconnect_button")
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = EmergencyRed)
                            Spacer(Modifier.size(6.dp))
                            Text(strings.disconnectBracelet, color = EmergencyRed, maxLines = 1)
                        }
                    }
                }
            }

            // Vitals Pair: Heart Rate & SpO2 (Strictly real BLE values or "--" when unavailable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MedBandCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateHealth() }
                        .testTag("home_hr_card"),
                    accentColor = EmergencyRed.copy(alpha = 0.4f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = strings.heartRate, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                        Text(strings.heartRate, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    val hrDisplay = if (isConnected && vitals?.heartRateBpm != null) "${vitals!!.heartRateBpm}" else "--"
                    Text(
                        text = hrDisplay,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.bpmUnit,
                        style = MaterialTheme.typography.labelMedium,
                        color = EmergencyRed
                    )
                    Spacer(Modifier.height(6.dp))
                    EcgWaveformCanvas(isLive = isConnected && vitals?.heartRateBpm != null, color = CyanPulse, modifier = Modifier.fillMaxWidth().height(36.dp))
                }

                MedBandCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateHealth() }
                        .testTag("home_spo2_card"),
                    accentColor = CyanPulse.copy(alpha = 0.45f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.WaterDrop, contentDescription = strings.spo2Title, tint = CyanPulse, modifier = Modifier.size(18.dp))
                        Text(strings.spo2Title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    val spo2Valid = isConnected && vitals?.spo2Valid == true && vitals?.spo2Percent != null
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularGaugeRing(
                            valueText = if (spo2Valid) "${vitals!!.spo2Percent}%" else "--",
                            unitText = "SpO2",
                            progress = if (spo2Valid) vitals!!.spo2Percent!! / 100f else null,
                            ringColor = CyanPulse,
                            size = 96.dp
                        )
                    }
                }
            }

            // Movement & Emergency Status Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MedBandCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateMovement() }
                        .testTag("home_movement_card")
                ) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = strings.movementStatus, tint = CyanPulse)
                    Spacer(Modifier.height(6.dp))
                    Text(strings.movementStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val moveText = if (!isConnected || motion == null) {
                        strings.qualityUnavailable
                    } else when (motion!!.activity) {
                        MovementActivity.RESTING -> strings.resting
                        MovementActivity.WALKING -> strings.walking
                        MovementActivity.ACTIVE -> strings.active
                        MovementActivity.UNUSUAL_MOVEMENT -> strings.unusualMovement
                        MovementActivity.POSSIBLE_FALL -> strings.possibleFall
                    }
                    Text(moveText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                MedBandCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateEmergency() }
                        .testTag("home_emergency_card"),
                    accentColor = if (activeEmergency) EmergencyRed else StatusGreen.copy(alpha = 0.55f)
                ) {
                    Icon(
                        imageVector = if (activeEmergency) Icons.Default.Emergency else Icons.Default.Shield,
                        contentDescription = strings.emergencyStatus,
                        tint = if (activeEmergency) EmergencyRed else StatusGreen
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(strings.emergencyStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusPill(
                        text = if (activeEmergency) strings.statusAlert else strings.statusSafe,
                        color = if (activeEmergency) EmergencyRed else StatusGreen
                    )
                }
            }

            // NFC Transfer & Quick Actions
            Button(
                onClick = onNavigateNfc,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("home_nfc_transfer_button")
            ) {
                Icon(Icons.Default.Nfc, contentDescription = null, tint = CyanPulse)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = strings.nfcMedicalTransfer,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onNavigateEmergency,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("home_emergency_quick_button")
                ) {
                    Icon(Icons.Default.Emergency, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(strings.openEmergency, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onNavigateHistory,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("home_history_quick_button")
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(strings.openHistory, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
