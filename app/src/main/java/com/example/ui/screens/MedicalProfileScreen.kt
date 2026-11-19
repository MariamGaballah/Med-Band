package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MedicalProfile
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.MedBandCard
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricBlue
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicalProfileScreen(
    viewModel: MedBandViewModel,
    onNavigateNfc: () -> Unit
) {
    val strings = LocalMedBandStrings.current
    val profileState by viewModel.medicalProfileState.collectAsStateWithLifecycle()
    val existing = (profileState as? UiState.Success)?.data

    var isEditing by remember { mutableStateOf(existing == null) }
    var fullName by remember { mutableStateOf("") }
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("O+") }
    var allergies by remember { mutableStateOf("") }
    var chronicDiseases by remember { mutableStateOf("") }
    var currentMedications by remember { mutableStateOf("") }
    var medicalNotes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        if (existing != null) {
            fullName = existing.fullName
            emergencyContactName = existing.emergencyContactName
            emergencyContactPhone = existing.emergencyContactPhone
            bloodGroup = existing.bloodGroup
            allergies = existing.allergies
            chronicDiseases = existing.chronicDiseases
            currentMedications = existing.currentMedications
            medicalNotes = existing.medicalNotes
            isEditing = false
        } else {
            isEditing = true
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.medicalProfileTitle, style = MaterialTheme.typography.headlineMedium)
                if (existing != null && !isEditing) {
                    OutlinedButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.testTag("profile_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.editProfile)
                    }
                }
            }

            MedBandCard(accentColor = CyanPulse.copy(alpha = 0.45f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CyanPulse)
                    Text(
                        text = strings.nfcSecurityNotice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!existing?.nfcTokenId.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${strings.nfcTokenLabel}: ${existing?.nfcTokenId}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPulse
                    )
                }
            }

            MedBandCard {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(strings.fullName) },
                    enabled = isEditing,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_full_name_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = emergencyContactName,
                    onValueChange = { emergencyContactName = it },
                    label = { Text(strings.emergencyContactName) },
                    enabled = isEditing,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_contact_name_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = emergencyContactPhone,
                    onValueChange = { emergencyContactPhone = it },
                    label = { Text(strings.emergencyContactPhone) },
                    enabled = isEditing,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_contact_phone_input")
                )

                Spacer(Modifier.height(12.dp))
                Text(strings.bloodGroup, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MedicalProfile.ALLOWED_BLOOD_GROUPS.forEach { group ->
                        FilterChip(
                            selected = bloodGroup == group,
                            onClick = { if (isEditing) bloodGroup = group },
                            enabled = isEditing || bloodGroup == group,
                            label = { Text(group) },
                            modifier = Modifier.testTag("profile_blood_$group")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text(strings.allergies) },
                    enabled = isEditing,
                    modifier = Modifier.fillMaxWidth().testTag("profile_allergies_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = chronicDiseases,
                    onValueChange = { chronicDiseases = it },
                    label = { Text(strings.chronicDiseases) },
                    enabled = isEditing,
                    modifier = Modifier.fillMaxWidth().testTag("profile_chronic_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = currentMedications,
                    onValueChange = { currentMedications = it },
                    label = { Text(strings.currentMedications) },
                    enabled = isEditing,
                    modifier = Modifier.fillMaxWidth().testTag("profile_medications_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = medicalNotes,
                    onValueChange = { medicalNotes = it },
                    label = { Text(strings.medicalNotes) },
                    enabled = isEditing,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("profile_notes_input")
                )

                if (validationError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(validationError!!, color = MaterialTheme.colorScheme.error)
                }

                if (isEditing) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (fullName.isBlank() || emergencyContactName.isBlank() || emergencyContactPhone.trim().length < 3) {
                                validationError = "Name and valid emergency contact are required."
                            } else {
                                validationError = null
                                viewModel.saveMedicalProfile(
                                    MedicalProfile(
                                        fullName = fullName,
                                        emergencyContactName = emergencyContactName,
                                        emergencyContactPhone = emergencyContactPhone,
                                        bloodGroup = bloodGroup,
                                        allergies = allergies,
                                        chronicDiseases = chronicDiseases,
                                        currentMedications = currentMedications,
                                        medicalNotes = medicalNotes,
                                        nfcTokenId = existing?.nfcTokenId ?: ""
                                    )
                                )
                                isEditing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("profile_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text(strings.saveProfile, color = Color.White)
                    }
                }
            }

            OutlinedButton(
                onClick = onNavigateNfc,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("profile_nfc_access_button")
            ) {
                Icon(Icons.Default.Nfc, contentDescription = null, tint = CyanPulse)
                Spacer(Modifier.size(8.dp))
                Text(strings.nfcMedicalTransfer)
            }
        }
    }
}
