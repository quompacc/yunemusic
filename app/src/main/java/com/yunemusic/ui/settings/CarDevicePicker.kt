package com.yunemusic.ui.settings

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yunemusic.R
import com.yunemusic.ui.theme.*

private data class PairedDevice(val address: String, val name: String, val isCarAudio: Boolean)

/**
 * Lets the user pick their car radio from the paired Bluetooth devices, then has the
 * system confirm it as a companion device. Shows its own list dialog while picking.
 *
 * The system's own chooser lists nearby scan results, often without names, so the app
 * lists paired devices by name and asks the system to confirm just that one address.
 * The companion association is what allows CarConnectionReceiver to open the car mode
 * from the background. BLUETOOTH_CONNECT is needed on Android 12+ to read device names
 * and to receive the connection broadcast at all.
 */
@Composable
fun rememberCarDevicePicker(
    onPicked: (address: String, name: String?) -> Unit,
    onFailed: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnPicked = rememberUpdatedState(onPicked)
    val currentOnFailed = rememberUpdatedState(onFailed)
    var candidates by remember { mutableStateOf<List<PairedDevice>?>(null) }
    var confirming by remember { mutableStateOf<PairedDevice?>(null) }

    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val device = confirming
        confirming = null
        if (result.resultCode == Activity.RESULT_OK && device != null) {
            releaseOtherAssociations(context, keep = device.address)
            currentOnPicked.value(device.address, device.name)
        }
    }

    fun associate(device: PairedDevice) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
        ) {
            currentOnFailed.value()
            return
        }
        confirming = device
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(device.address).build())
            .setSingleDevice(true)
            .build()
        cdm.associate(request, object : CompanionDeviceManager.Callback() {
            // Android 13+ routes onAssociationPending here by default.
            @Deprecated("Replaced by onAssociationPending on API 33")
            override fun onDeviceFound(chooser: IntentSender) {
                confirmLauncher.launch(IntentSenderRequest.Builder(chooser).build())
            }

            override fun onFailure(error: CharSequence?) {
                confirming = null
                currentOnFailed.value()
            }
        }, null)
    }

    fun showPairedDevices() {
        val paired = pairedDevices(context)
        if (paired.isEmpty()) currentOnFailed.value() else candidates = paired
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showPairedDevices() else currentOnFailed.value() }

    candidates?.let { devices ->
        PairedDeviceDialog(
            devices = devices,
            onPick = { device ->
                candidates = null
                associate(device)
            },
            onDismiss = { candidates = null }
        )
    }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            showPairedDevices()
        }
    }
}

@Composable
private fun PairedDeviceDialog(
    devices: List<PairedDevice>,
    onPick: (PairedDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_car_mode_pick_title), color = TextPrimary) },
        text = {
            LazyColumn {
                items(devices, key = { it.address }) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(device) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (device.isCarAudio) Icons.Default.DirectionsCar else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (device.isCarAudio) SignalOrange else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(device.name, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel), color = TextSecondary)
            }
        },
        containerColor = SurfaceVariantDark
    )
}

/** Paired devices by name, car radios first; requires BLUETOOTH_CONNECT on Android 12+. */
private fun pairedDevices(context: Context): List<PairedDevice> = try {
    context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
        .map { device ->
            val deviceClass = device.bluetoothClass?.deviceClass
            PairedDevice(
                address = device.address,
                name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                isCarAudio = deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                    deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
            )
        }
        .sortedWith(compareByDescending<PairedDevice> { it.isCarAudio }.thenBy { it.name.lowercase() })
} catch (_: SecurityException) {
    emptyList()
}

/** Only one car is used, so older associations would just keep stale permissions alive. */
@Suppress("DEPRECATION")
private fun releaseOtherAssociations(context: Context, keep: String) {
    val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.myAssociations
                .filter { !it.deviceMacAddress?.toString().equals(keep, ignoreCase = true) }
                .forEach { cdm.disassociate(it.id) }
        } else {
            cdm.associations
                .filter { !it.equals(keep, ignoreCase = true) }
                .forEach { cdm.disassociate(it) }
        }
    } catch (_: Exception) {
        // Cleanup only; a leftover association does no harm.
    }
}
