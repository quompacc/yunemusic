package com.yunemusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunemusic.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCarModeNavigate: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Taste Profile zurucksetzen?", color = TextPrimary) },
            text = {
                Text(
                    "Alle Horpraferenzen werden geloscht. Diese Aktion kann nicht ruckgangig gemacht werden.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetTasteProfile()
                        showResetDialog = false
                    }
                ) {
                    Text("Zurucksetzen", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Abbrechen", color = TextSecondary)
                }
            },
            containerColor = SurfaceVariantDark
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Einstellungen",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Audio Quality Section
            item {
                SettingsSectionHeader("Audio")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Audio-Qualitat",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val qualities = listOf("Niedrig (~128 kbps)", "Mittel (~256 kbps)", "Hoch (Beste Qualität)")
                        qualities.forEachIndexed { index, label ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.audioQuality == index,
                                    onClick = { viewModel.updateAudioQuality(index) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = SignalOrange,
                                        unselectedColor = TextTertiary
                                    )
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (uiState.audioQuality == index) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lautstärke-Begrenzer",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Verhindert Verzerrung bei maximaler Lautstärke",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                        Switch(
                            checked = uiState.loudnessLimiter,
                            onCheckedChange = { viewModel.updateLoudnessLimiter(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimary,
                                checkedTrackColor = SignalOrange,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = OutlineDark
                            )
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bei Benachrichtigungen leiser",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Musik wird kurz leiser, wenn Nachrichten eintreffen. Benötigt Benachrichtigungszugriff.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                        Switch(
                            checked = uiState.duckOnNotification,
                            onCheckedChange = { enabled ->
                                viewModel.updateDuckOnNotification(enabled)
                                if (enabled) {
                                    val hasAccess = NotificationManagerCompat
                                        .getEnabledListenerPackages(context)
                                        .contains(context.packageName)
                                    if (!hasAccess) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        )
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimary,
                                checkedTrackColor = SignalOrange,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = OutlineDark
                            )
                        )
                    }
                }
            }

            // Data Mode Section
            item {
                SettingsSectionHeader("Netzwerk")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nur WLAN",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Streaming nur uber WLAN erlauben",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                        Switch(
                            checked = uiState.wifiOnly,
                            onCheckedChange = { viewModel.updateWifiOnly(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimary,
                                checkedTrackColor = SignalOrange,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = OutlineDark
                            )
                        )
                    }
                }
            }

            // Car Mode Section
            item {
                SettingsSectionHeader("Car Mode")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Aktivierung",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val triggers = listOf("Manuell", "Bluetooth")
                        val triggerValues = listOf("manual", "bluetooth")
                        triggers.forEachIndexed { index, label ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.carModeTrigger == triggerValues[index],
                                    onClick = { viewModel.updateCarModeTrigger(triggerValues[index]) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = SignalOrange,
                                        unselectedColor = TextTertiary
                                    )
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (uiState.carModeTrigger == triggerValues[index]) TextPrimary else TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onCarModeNavigate,
                            colors = ButtonDefaults.buttonColors(containerColor = SignalContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = OnSignalContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Car Mode offnen", color = OnSignalContainer)
                        }
                    }
                }
            }

            // Profile Section
            item {
                SettingsSectionHeader("Profil")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Taste Profile",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Deine Horpraferenzen werden automatisch aus deiner Wiedergabe gelernt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, ErrorRed.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Taste Profile zurucksetzen")
                        }
                    }
                }
            }

            // Success/error snackbar placeholder
            uiState.message?.let { message ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isError) ErrorRed.copy(alpha = 0.2f)
                            else SuccessGreen.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = if (uiState.isError) ErrorRed else SuccessGreen,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = SignalLight,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}
