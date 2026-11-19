package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalMedBandStrings
import com.example.ui.components.MedBandCard
import com.example.ui.components.StatusPill
import com.example.ui.components.severityColor
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.MedBandViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NotificationsScreen(
    viewModel: MedBandViewModel
) {
    val strings = LocalMedBandStrings.current
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
    val alerts = (alertsState as? UiState.Success)?.data.orEmpty()
    val fmt = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.notificationsTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (alerts.any { !it.acknowledged }) {
                    OutlinedButton(
                        onClick = { viewModel.acknowledgeAllAlerts() },
                        modifier = Modifier.testTag("alerts_mark_all_read_button")
                    ) {
                        Text(strings.markAllRead)
                    }
                }
            }

            if (alerts.isEmpty()) {
                MedBandCard {
                    Text(strings.noAlertsYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                alerts.forEach { alert ->
                    val sevColor = severityColor(alert.severity)
                    MedBandCard(
                        accentColor = if (!alert.acknowledged) sevColor else MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .clickable {
                                if (!alert.acknowledged && alert.id.isNotBlank()) {
                                    viewModel.acknowledgeAlert(alert.id)
                                }
                            }
                            .testTag("alert_item_${alert.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(text = alert.category, color = sevColor)
                            StatusPill(
                                text = if (alert.acknowledged) strings.acknowledgedLabel else strings.unreadLabel,
                                color = if (alert.acknowledged) StatusGreen else CyanPulse
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(alert.title, style = MaterialTheme.typography.titleMedium)
                        Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = alert.createdAt?.toDate()?.let { fmt.format(it) } ?: "--",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
