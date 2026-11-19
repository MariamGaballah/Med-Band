package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BleConnectionState
import com.example.hardware.BleProtocol
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.AnimatedConnectionBadge
import com.example.ui.components.MedBandCard
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.MedBandViewModel

@Composable
fun BluetoothScreen(
    viewModel: MedBandViewModel
) {
    val strings = LocalMedBandStrings.current
    val bleManager = viewModel.bleManager
    val bleState by bleManager.connectionState.collectAsStateWithLifecycle()
    val devices by bleManager.discoveredDevices.collectAsStateWithLifecycle()
    val connectedDevice by bleManager.connectedDevice.collectAsStateWithLifecycle()
    val rssi by bleManager.rssi.collectAsStateWithLifecycle()
    val lastError by bleManager.lastError.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var hasPermissions by remember { mutableStateOf(bleManager.hasBlePermissions()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions && bleManager.isBluetoothEnabled()) {
            bleManager.startScan()
        }
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
            Text(strings.bleTitle, style = MaterialTheme.typography.headlineMedium)
            Text(strings.bleSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Status Card
            MedBandCard(
                accentColor = when (bleState) {
                    BleConnectionState.CONNECTED -> StatusGreen
                    BleConnectionState.ERROR -> EmergencyRed
                    else -> CyanPulse.copy(alpha = 0.5f)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedConnectionBadge(
                        label = when (bleState) {
                            BleConnectionState.CONNECTED -> strings.braceletConnected
                            BleConnectionState.SCANNING -> strings.scanning
                            BleConnectionState.CONNECTING, BleConnectionState.DISCOVERING_SERVICES -> strings.braceletConnecting
                            else -> strings.braceletDisconnected
                        },
                        color = when (bleState) {
                            BleConnectionState.CONNECTED -> StatusGreen
                            BleConnectionState.ERROR -> EmergencyRed
                            BleConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> StatusAmber
                        },
                        isPulsing = bleState == BleConnectionState.SCANNING || bleState == BleConnectionState.CONNECTING || bleState == BleConnectionState.CONNECTED
                    )

                    if (bleState == BleConnectionState.CONNECTING || bleState == BleConnectionState.DISCOVERING_SERVICES || bleState == BleConnectionState.SCANNING) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    }
                }

                if (connectedDevice != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(connectedDevice!!.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${connectedDevice!!.address}  •  ${strings.signalRssi}: ${rssi ?: connectedDevice!!.rssi} dBm",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPulse
                    )
                }

                if (!lastError.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(lastError!!, style = MaterialTheme.typography.bodyMedium, color = EmergencyRed)
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.autoReconnect, style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = prefs.autoReconnectBle,
                        onCheckedChange = { viewModel.setAutoReconnect(it) },
                        modifier = Modifier.testTag("ble_auto_reconnect_switch")
                    )
                }
            }

            if (!hasPermissions) {
                MedBandCard(accentColor = StatusAmber) {
                    Text(strings.permissionsRequired, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { permissionLauncher.launch(bleManager.requiredPermissions().toTypedArray()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("ble_grant_permissions_button")
                    ) {
                        Text(strings.grantPermissions)
                    }
                }
            } else {
                if (!bleManager.isBluetoothEnabled()) {
                    MedBandCard(accentColor = EmergencyRed) {
                        Text(strings.bluetoothOffWarning, style = MaterialTheme.typography.bodyLarge, color = EmergencyRed)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (bleState == BleConnectionState.SCANNING) bleManager.stopScan()
                            else bleManager.startScan()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("ble_scan_button")
                    ) {
                        Icon(
                            imageVector = if (bleState == BleConnectionState.SCANNING) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = if (bleState == BleConnectionState.SCANNING) strings.stopScan else strings.scanDevices,
                            color = Color.White
                        )
                    }

                    if (bleState == BleConnectionState.CONNECTED) {
                        OutlinedButton(
                            onClick = { bleManager.disconnect() },
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("ble_disconnect_button")
                        ) {
                            Text(strings.disconnect, color = EmergencyRed)
                        }
                    }
                }
            }

            // Hardware Command Controls when Connected
            if (bleState == BleConnectionState.CONNECTED) {
                MedBandCard(accentColor = CyanPulse.copy(alpha = 0.5f)) {
                    Text(strings.hardwareTestTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { bleManager.sendCommand(BleProtocol.buildBuzzerTestCommand()) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("ble_test_buzzer_button")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.testBuzzer)
                        }
                        FilledTonalButton(
                            onClick = { bleManager.sendCommand(BleProtocol.buildVibrationTestCommand()) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("ble_test_vibration_button")
                        ) {
                            Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.testVibration)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { bleManager.sendCommand(BleProtocol.buildSyncNowCommand()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("ble_sync_now_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(strings.syncNow)
                    }
                }
            }

            // Discovered Devices
            if (devices.isEmpty()) {
                MedBandCard {
                    Text(
                        text = strings.noDevicesFound,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                devices.forEach { device ->
                    val isThisConnected = connectedDevice?.address == device.address && bleState == BleConnectionState.CONNECTED
                    MedBandCard(
                        accentColor = if (device.name.contains(BleProtocol.DEVICE_NAME_PREFIX, ignoreCase = true)) CyanPulse else MaterialTheme.colorScheme.outline
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = if (isThisConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isThisConnected) StatusGreen else CyanPulse,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(device.address, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyanPulse)
                                    Text("${device.rssi} dBm", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Button(
                                onClick = {
                                    if (isThisConnected) bleManager.disconnect()
                                    else bleManager.connectToDevice(device.address, device.name)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ble_device_action_${device.address}")
                            ) {
                                Text(if (isThisConnected) strings.disconnect else strings.connect)
                            }
                        }
                    }
                }
            }

            // GATT Spec Summary
            MedBandCard {
                Text(strings.gattSpecTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Service: ${BleProtocol.SERVICE_UUID}", style = MaterialTheme.typography.labelMedium, color = CyanPulse)
                Text("Vitals:  ${BleProtocol.CHAR_VITALS_UUID}", style = MaterialTheme.typography.labelMedium)
                Text("Motion:  ${BleProtocol.CHAR_MOTION_UUID}", style = MaterialTheme.typography.labelMedium)
                Text("Status:  ${BleProtocol.CHAR_STATUS_UUID}", style = MaterialTheme.typography.labelMedium)
                Text("Command: ${BleProtocol.CHAR_COMMAND_UUID}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
