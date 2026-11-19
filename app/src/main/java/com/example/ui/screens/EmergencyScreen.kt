package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.GoogleMapsAttributionFooter
import com.example.ui.components.MedBandCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedDeep
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EmergencyScreen(
    viewModel: MedBandViewModel
) {
    val strings = LocalMedBandStrings.current
    val context = LocalContext.current
    val emergenciesState by viewModel.emergenciesState.collectAsStateWithLifecycle()
    val profileState by viewModel.medicalProfileState.collectAsStateWithLifecycle()
    val mapsState by viewModel.mapsCareState.collectAsStateWithLifecycle()
    val braceletAlertActive by viewModel.braceletAlertActive.collectAsStateWithLifecycle()
    val pendingCountdown by viewModel.pendingFallCountdownSec.collectAsStateWithLifecycle()

    val events = (emergenciesState as? UiState.Success)?.data.orEmpty()
    val activeEvent = events.firstOrNull { it.status == "PENDING_CONFIRMATION" || it.status == "CONFIRMED" }
    val profile = (profileState as? UiState.Success)?.data
    val dateFmt = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.searchNearbyMedicalCare()
    }

    val bgGradient = if (activeEvent != null) {
        Brush.verticalGradient(listOf(EmergencyRedDeep.copy(alpha = 0.72f), MaterialTheme.colorScheme.background))
    } else {
        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
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
            Text(strings.emergencyTitle, style = MaterialTheme.typography.headlineMedium)

            // Active / Pending Emergency Hero Card
            MedBandCard(
                accentColor = if (activeEvent != null) EmergencyRed else MaterialTheme.colorScheme.outline,
                containerColor = if (activeEvent != null) EmergencyRedDeep.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeEvent != null) Icons.Default.Warning else Icons.Default.Emergency,
                            contentDescription = strings.emergencyTitle,
                            tint = EmergencyRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    if (activeEvent != null) {
                        val isPending = activeEvent.status == "PENDING_CONFIRMATION"
                        Text(
                            text = if (isPending) strings.emergencyPendingBanner else strings.emergencyActiveBanner,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (isPending && pendingCountdown != null) {
                            Text(
                                text = "${pendingCountdown}s",
                                style = MaterialTheme.typography.displayMedium,
                                color = StatusAmber
                            )
                        }
                        if (activeEvent.locationLabel.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(18.dp))
                                Text(activeEvent.locationLabel, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isPending) {
                                Button(
                                    onClick = { viewModel.confirmEmergency(activeEvent.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("emergency_confirm_button")
                                ) {
                                    Text(strings.confirmEmergency, color = Color.White)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.resolveEmergency(activeEvent.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("emergency_resolve_button")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black)
                                    Spacer(Modifier.size(6.dp))
                                    Text(strings.resolveEmergency, color = Color.Black)
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelEmergency(activeEvent.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("emergency_cancel_button")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.size(4.dp))
                                Text(strings.cancelAlert, color = Color.White)
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.triggerEmergency("SOS_MANUAL", pendingOnly = false, notes = "User pressed SOS") },
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("emergency_sos_button")
                        ) {
                            Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.size(8.dp))
                            Text(strings.triggerSos, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                    }

                    if (braceletAlertActive) {
                        FilledTonalButton(
                            onClick = { viewModel.activateBraceletAlert(false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("emergency_silence_bracelet_button")
                        ) {
                            Icon(Icons.Default.VolumeOff, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(strings.silenceBraceletAlert)
                        }
                    }
                }
            }

            // Emergency Contact Card (Honest OS Intent Dispatch)
            MedBandCard {
                Text(strings.emergencyContactCard, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                val contactName = profile?.emergencyContactName?.ifBlank { null } ?: "--"
                val contactPhone = profile?.emergencyContactPhone?.ifBlank { null } ?: "--"
                Text("$contactName  •  $contactPhone", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strings.deliveryHonestyNotice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val phone = profile?.emergencyContactPhone ?: return@Button
                            val locText = activeEvent?.locationLabel?.takeIf { it.isNotBlank() } ?: "Unknown location"
                            val coords = if (activeEvent?.latitude != null && activeEvent.longitude != null) {
                                "https://maps.google.com/?q=${activeEvent.latitude},${activeEvent.longitude}"
                            } else ""
                            val body = "MedBand Alert: ${profile.fullName} needs assistance. Location: $locText $coords"
                            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                                putExtra("sms_body", body)
                            }
                            context.startActivity(smsIntent)
                            if (activeEvent != null) {
                                viewModel.markEmergencyDispatchedViaOsIntent(activeEvent.id)
                            }
                        },
                        enabled = !profile?.emergencyContactPhone.isNullOrBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("emergency_sms_intent_button")
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.sendSmsIntent, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            val phone = profile?.emergencyContactPhone ?: return@OutlinedButton
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        },
                        enabled = !profile?.emergencyContactPhone.isNullOrBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("emergency_dial_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.callContactIntent)
                    }
                }
            }

            // Nearby Medical Care via Google Maps Grounding
            MedBandCard(accentColor = CyanPulse.copy(alpha = 0.5f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LocalHospital, contentDescription = null, tint = CyanPulse)
                    Text(strings.nearbyCareTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strings.mapsGroundingDisclaimer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLoc) {
                            viewModel.searchNearbyMedicalCare()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    },
                    enabled = mapsState !is UiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("emergency_find_nearby_care_button")
                ) {
                    if (mapsState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text(strings.findingNearbyCare, color = Color.White)
                    } else {
                        Text(strings.findNearbyCare, color = Color.White)
                    }
                }

                when (val state = mapsState) {
                    is UiState.Success -> {
                        Spacer(Modifier.height(12.dp))
                        if (state.data.summaryText.isNotBlank()) {
                            Text(state.data.summaryText, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.data.sources.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            state.data.sources.forEach { src ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(src.uri)))
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = src.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CyanPulse,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = strings.openInMaps, tint = CyanPulse, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            GoogleMapsAttributionFooter()
                        }
                    }
                    is UiState.Error -> {
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = EmergencyRed)
                    }
                    else -> Unit
                }
            }

            // Emergency History
            Text(strings.emergencyHistory, style = MaterialTheme.typography.titleMedium)
            events.take(15).forEach { ev ->
                MedBandCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ev.triggerType, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${ev.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "--"}  •  ${ev.notificationStatus}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = ev.status,
                            color = when (ev.status) {
                                "CONFIRMED" -> EmergencyRed
                                "PENDING_CONFIRMATION" -> StatusAmber
                                else -> StatusGreen
                            }
                        )
                    }
                }
            }
        }
    }
}
