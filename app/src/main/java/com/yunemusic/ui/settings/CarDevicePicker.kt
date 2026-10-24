package com.yunemusic.ui.settings

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Lets the user pick their car radio in the system's companion device dialog.
 *
 * The companion association is what allows CarConnectionReceiver to open the car mode
 * from the background. BLUETOOTH_CONNECT is needed on Android 12+ to receive the
 * connection broadcast at all.
 */
@Composable
fun rememberCarDevicePicker(
    onPicked: (address: String, name: String?) -> Unit,
    onFailed: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnPicked = rememberUpdatedState(onPicked)
    val currentOnFailed = rememberUpdatedState(onFailed)

    val chooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val picked = result.data?.let { pickedDevice(context, it) }
        if (picked != null) {
            releaseOtherAssociations(context, keep = picked.first)
            currentOnPicked.value(picked.first, picked.second)
        }
    }

    fun associate() {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
        ) {
            currentOnFailed.value()
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()
        cdm.associate(request, object : CompanionDeviceManager.Callback() {
            // Android 13+ routes onAssociationPending here by default.
            @Deprecated("Replaced by onAssociationPending on API 33")
            override fun onDeviceFound(chooser: IntentSender) {
                chooserLauncher.launch(IntentSenderRequest.Builder(chooser).build())
            }

            override fun onFailure(error: CharSequence?) {
                currentOnFailed.value()
            }
        }, null)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) associate() else currentOnFailed.value() }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            associate()
        }
    }
}

@Suppress("DEPRECATION")
private fun pickedDevice(context: Context, data: Intent): Pair<String, String?>? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
            ?.let { info ->
                val address = info.deviceMacAddress?.toString()?.uppercase() ?: return null
                return address to (info.displayName?.toString() ?: bondedName(context, address))
            }
    }
    val device: BluetoothDevice = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        ?: return null
    return device.address to bondedName(context, device.address)
}

private fun bondedName(context: Context, address: String): String? = try {
    context.getSystemService(android.bluetooth.BluetoothManager::class.java)
        ?.adapter?.getRemoteDevice(address)?.name
} catch (_: SecurityException) {
    null
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
