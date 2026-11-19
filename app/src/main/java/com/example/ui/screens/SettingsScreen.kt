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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.performSignOut
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.MedBandCard
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmergencyRed
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: MedBandViewModel,
    onNavigateBluetooth: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    val strings = LocalMedBandStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val accountState by viewModel.userAccountState.collectAsStateWithLifecycle()
    val account = (accountState as? UiState.Success)?.data

    var hrMin by remember { mutableFloatStateOf(50f) }
    var hrMax by remember { mutableFloatStateOf(130f) }
    var spo2Min by remember { mutableFloatStateOf(92f) }
    var fallEnabled by remember { mutableStateOf(true) }
    var intervalSec by remember { mutableFloatStateOf(2f) }

    LaunchedEffect(account, prefs) {
        if (account != null) {
            hrMin = account.hrMinThreshold.toFloat()
            hrMax = account.hrMaxThreshold.toFloat()
            spo2Min = account.spo2MinThreshold.toFloat()
            fallEnabled = account.fallDetectionEnabled
        }
        intervalSec = prefs.measurementIntervalSec.toFloat()
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
            Text(strings.settingsTitle, style = MaterialTheme.typography.headlineMedium)

            // Language & Theme
            MedBandCard {
                Text(strings.languageSetting, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.languageCode == "ar",
                        onClick = { viewModel.setLanguage("ar") },
                        label = { Text("العربية (RTL)") },
                        modifier = Modifier.testTag("settings_lang_ar")
                    )
                    FilterChip(
                        selected = prefs.languageCode == "en",
                        onClick = { viewModel.setLanguage("en") },
                        label = { Text("English (LTR)") },
                        modifier = Modifier.testTag("settings_lang_en")
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(strings.themeSetting, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.darkTheme,
                        onClick = { viewModel.setDarkTheme(true) },
                        label = { Text(strings.darkTheme) },
                        modifier = Modifier.testTag("settings_theme_dark")
                    )
                    FilterChip(
                        selected = !prefs.darkTheme,
                        onClick = { viewModel.setDarkTheme(false) },
                        label = { Text(strings.lightTheme) },
                        modifier = Modifier.testTag("settings_theme_light")
                    )
                }
            }

            // Thresholds & Measurement Interval
            MedBandCard {
                Text("${strings.hrThresholds}: ${hrMin.roundToInt()} - ${hrMax.roundToInt()} BPM", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = hrMin,
                    onValueChange = { hrMin = it.coerceAtMost(hrMax - 15f) },
                    valueRange = 35f..100f
                )
                Slider(
                    value = hrMax,
                    onValueChange = { hrMax = it.coerceAtLeast(hrMin + 15f) },
                    valueRange = 90f..200f
                )

                Spacer(Modifier.height(8.dp))
                Text("${strings.spo2Threshold}: ${spo2Min.roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = spo2Min,
                    onValueChange = { spo2Min = it },
                    valueRange = 75f..96f
                )

                Spacer(Modifier.height(8.dp))
                Text("${strings.measurementInterval}: ${intervalSec.roundToInt()}s", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = intervalSec,
                    onValueChange = { intervalSec = it },
                    valueRange = 1f..15f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.fallDetectionToggle, style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = fallEnabled,
                        onCheckedChange = { fallEnabled = it },
                        modifier = Modifier.testTag("settings_fall_toggle")
                    )
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.updateThresholdSettings(
                            hrMin = hrMin.roundToInt().toLong(),
                            hrMax = hrMax.roundToInt().toLong(),
                            spo2Min = spo2Min.roundToInt().toLong(),
                            fallEnabled = fallEnabled,
                            intervalSec = intervalSec.roundToInt()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_save_button")
                ) {
                    Text(strings.saveSettings, color = Color.White)
                }
            }

            // Quick Shortcuts & Data Management
            MedBandCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onNavigateBluetooth,
                        modifier = Modifier.weight(1f).testTag("settings_ble_shortcut")
                    ) {
                        Text(strings.navBluetooth)
                    }
                    OutlinedButton(
                        onClick = onNavigateProfile,
                        modifier = Modifier.weight(1f).testTag("settings_profile_shortcut")
                    ) {
                        Text(strings.navProfile)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val csv = viewModel.buildExportCsvReport()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_SUBJECT, "MedBand Data Export")
                            putExtra(Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, strings.exportReport))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("settings_export_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(strings.exportReport)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.clearMeasurementHistory() },
                    modifier = Modifier.fillMaxWidth().testTag("settings_clear_history_button")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = EmergencyRed)
                    Spacer(Modifier.size(8.dp))
                    Text(strings.deleteAllHistory, color = EmergencyRed)
                }
            }

            // About & Sign Out
            MedBandCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyanPulse)
                    Text(strings.aboutMedBand, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(strings.aboutDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = {
                    performSignOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onBeforeSignOut = {
                            viewModel.bleManager.disconnect()
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("settings_logout_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White)
                Spacer(Modifier.size(8.dp))
                Text(strings.logout, color = Color.White)
            }
        }
    }
}
