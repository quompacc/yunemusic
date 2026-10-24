package com.yunemusic.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunemusic.R
import com.yunemusic.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCarModeNavigate: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }
    val context = LocalContext.current
    val pickCarDevice = rememberCarDevicePicker(
        onPicked = viewModel::updateCarModeDevice,
        onFailed = viewModel::onCarDevicePickFailed
    )

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_backup_import_dialog_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.settings_backup_import_dialog_text), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackup(uri)
                    pendingImport = null
                }) {
                    Text(stringResource(R.string.settings_backup_import_confirm), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.settings_cancel), color = TextSecondary)
                }
            },
            containerColor = SurfaceVariantDark
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title), color = TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.settings_reset_dialog_text),
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
                    Text(stringResource(R.string.settings_reset_confirm), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.settings_cancel), color = TextSecondary)
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
                    text = stringResource(R.string.settings_title),
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
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_prefetch_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.settings_prefetch_desc),
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(checked = uiState.prefetchEnabled,
                            onCheckedChange = viewModel::updatePrefetchEnabled)
                    }
                }
            }
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_audio))
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
                            text = stringResource(R.string.settings_audio_quality),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val qualities = listOf(
                            stringResource(R.string.settings_quality_low),
                            stringResource(R.string.settings_quality_medium),
                            stringResource(R.string.settings_quality_high)
                        )
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
                                text = stringResource(R.string.settings_loudness_limiter_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_loudness_limiter_desc),
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
                                text = stringResource(R.string.settings_duck_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_duck_desc),
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
                SettingsSectionHeader(stringResource(R.string.settings_section_network))
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
                                text = stringResource(R.string.settings_wifi_only_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_wifi_only_desc),
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
                SettingsSectionHeader(stringResource(R.string.settings_section_car_mode))
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
                            text = stringResource(R.string.settings_car_mode_activation),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val triggers = listOf(
                            stringResource(R.string.settings_car_mode_manual),
                            stringResource(R.string.settings_car_mode_bluetooth)
                        )
                        val triggerValues = listOf("manual", "bluetooth")
                        triggers.forEachIndexed { index, label ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.carModeTrigger == triggerValues[index],
                                    onClick = {
                                        // Bluetooth needs a picked car radio, otherwise any device would count.
                                        if (triggerValues[index] == "bluetooth" && uiState.carModeDeviceAddress == null) {
                                            pickCarDevice()
                                        } else {
                                            viewModel.updateCarModeTrigger(triggerValues[index])
                                        }
                                    },
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
                        if (uiState.carModeTrigger == "bluetooth" && uiState.carModeDeviceAddress != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_car_mode_device,
                                    uiState.carModeDeviceName ?: uiState.carModeDeviceAddress!!
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_car_mode_device_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            TextButton(onClick = pickCarDevice) {
                                Text(stringResource(R.string.settings_car_mode_change_device), color = SignalOrange)
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
                            Text(stringResource(R.string.settings_open_car_mode), color = OnSignalContainer)
                        }
                    }
                }
            }

            // Profile Section
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_profile))
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
                            text = stringResource(R.string.settings_taste_profile),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_taste_profile_desc),
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
                            Text(stringResource(R.string.settings_reset_taste_profile))
                        }
                    }
                }
            }

            // Backup Section
            item {
                SettingsSectionHeader(stringResource(R.string.settings_section_backup))
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
                            text = stringResource(R.string.settings_backup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { exportLauncher.launch("yunemusic-backup-${java.time.LocalDate.now()}.json") },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                tint = OnSignalContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_backup_export), color = OnSignalContainer)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            // Some file providers report JSON files as generic binary or text.
                            onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_backup_import), color = TextPrimary)
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
                            text = stringResource(message),
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
