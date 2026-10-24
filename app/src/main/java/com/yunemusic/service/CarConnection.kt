package com.yunemusic.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.yunemusic.MainActivity
import com.yunemusic.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val ACTION_OPEN_CAR_MODE = "com.yunemusic.ACTION_OPEN_CAR_MODE"

/**
 * Opens the car mode when the car radio the user picked connects. Only that one address
 * counts, so headphones and other Bluetooth devices never trigger it.
 *
 * Starting an activity from the background is allowed here only because the radio was
 * picked through CompanionDeviceManager; see CarDevicePicker.
 */
class CarConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val address = intent.bluetoothDeviceAddress() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isCarModeDevice(UserPreferences(context.applicationContext), address)) {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .setAction(ACTION_OPEN_CAR_MODE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }
            } catch (e: Exception) {
                Log.w("CarConnection", "Car Mode konnte nicht geöffnet werden: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}

suspend fun isCarModeDevice(prefs: UserPreferences, address: String): Boolean =
    prefs.carModeTrigger.first() == "bluetooth" &&
        prefs.carModeDeviceAddress.first().equals(address, ignoreCase = true)

@Suppress("DEPRECATION")
fun Intent.bluetoothDeviceAddress(): String? {
    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
    return device?.address
}
