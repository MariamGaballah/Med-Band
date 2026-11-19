package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hardware.NfcAvailability
import com.example.hardware.NfcMedicalManager
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.MedBandCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun NfcTransferScreen(
    viewModel: MedBandViewModel
) {
    val strings = LocalMedBandStrings.current
    val context = LocalContext.current
    val activity = context as? Activity
    val availability = NfcMedicalManager.checkAvailability(context)

    val writeMode by viewModel.nfcWriteInProgress.collectAsStateWithLifecycle()
    val scanResult by viewModel.nfcScanResult.collectAsStateWithLifecycle()
    val parsedToken by viewModel.lastParsedNfcToken.collectAsStateWithLifecycle()
    val linkedTagsState by viewModel.nfcTokensState.collectAsStateWithLifecycle()

    DisposableEffect(activity, availability) {
        if (activity != null && availability == NfcAvailability.READY) {
            NfcMedicalManager.enableReaderMode(activity) { tag ->
                viewModel.handleNfcTagDiscovered(context, tag)
            }
        }
        onDispose {
            if (activity != null) {
                NfcMedicalManager.disableReaderMode(activity)
            }
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
            Text(strings.nfcTitle, style = MaterialTheme.typography.headlineMedium)

            MedBandCard(
                accentColor = when (availability) {
                    NfcAvailability.READY -> StatusGreen
                    NfcAvailability.DISABLED -> StatusAmber
                    NfcAvailability.UNSUPPORTED -> EmergencyRed
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Nfc, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(28.dp))
                    Column {
                        Text(
                            text = when (availability) {
                                NfcAvailability.READY -> strings.nfcSupported
                                NfcAvailability.DISABLED -> strings.nfcDisabled
                                NfcAvailability.UNSUPPORTED -> strings.nfcNotSupported
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (availability == NfcAvailability.READY) {
                            Text(strings.tapTagPrompt, style = MaterialTheme.typography.bodyMedium, color = CyanPulse)
                        }
                    }
                }
            }

            // Mode Toggle: Read vs Write Opaque Token
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !writeMode,
                    onClick = { viewModel.setNfcWriteMode(false) },
                    label = { Text(strings.nfcReadMode) },
                    modifier = Modifier.testTag("nfc_read_mode_chip")
                )
                FilterChip(
                    selected = writeMode,
                    onClick = { viewModel.setNfcWriteMode(true) },
                    label = { Text(strings.nfcWriteMode) },
                    modifier = Modifier.testTag("nfc_write_mode_chip")
                )
            }

            MedBandCard(accentColor = CyanPulse.copy(alpha = 0.45f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CyanPulse)
                    Text(strings.nfcSecurityNotice, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Scan / Verification Result
            when (val res = scanResult) {
                is UiState.Loading -> {
                    MedBandCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Text("Verifying NFC token...")
                        }
                    }
                }
                is UiState.Success -> {
                    val profile = res.data
                    if (profile != null) {
                        MedBandCard(accentColor = StatusGreen) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = StatusGreen)
                                Text(strings.authorizedProfilePreview, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (parsedToken != null) {
                                Text("Tag UID: ${parsedToken!!.tagUidHex} • Token: ${parsedToken!!.tokenId}", style = MaterialTheme.typography.labelMedium, color = CyanPulse)
                                Spacer(Modifier.height(6.dp))
                            }
                            Text("${strings.fullName}: ${profile.fullName}", style = MaterialTheme.typography.bodyLarge)
                            Text("${strings.bloodGroup}: ${profile.bloodGroup}", style = MaterialTheme.typography.bodyLarge, color = EmergencyRed)
                            Text("${strings.allergies}: ${profile.allergies.ifBlank { "--" }}", style = MaterialTheme.typography.bodyMedium)
                            Text("${strings.chronicDiseases}: ${profile.chronicDiseases.ifBlank { "--" }}", style = MaterialTheme.typography.bodyMedium)
                            Text("${strings.currentMedications}: ${profile.currentMedications.ifBlank { "--" }}", style = MaterialTheme.typography.bodyMedium)
                            Text("${strings.emergencyContactName}: ${profile.emergencyContactName} (${profile.emergencyContactPhone})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is UiState.Error -> {
                    MedBandCard(accentColor = EmergencyRed) {
                        Text(res.message, style = MaterialTheme.typography.bodyLarge, color = EmergencyRed)
                    }
                }
                UiState.Idle -> Unit
            }

            // Linked NFC Tags
            Text(strings.linkedTagsTitle, style = MaterialTheme.typography.titleMedium)
            val tags = (linkedTagsState as? UiState.Success)?.data.orEmpty()
            tags.forEach { token ->
                MedBandCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(token.label, style = MaterialTheme.typography.titleMedium)
                            Text("Token: ${token.tokenId} • UID: ${token.tagUidHex}", style = MaterialTheme.typography.labelMedium)
                        }
                        StatusPill(
                            text = if (token.active) "ACTIVE" else "REVOKED",
                            color = if (token.active) StatusGreen else EmergencyRed
                        )
                    }
                }
            }

            // iOS Compatibility Note
            MedBandCard {
                Text(strings.iosNfcLimitationNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
