package com.yunemusic

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.MediaStore
import androidx.activity.viewModels
import com.yunemusic.ui.player.PlayerViewModel
import com.yunemusic.service.voiceSearchQuery
import androidx.lifecycle.lifecycleScope
import com.yunemusic.data.preferences.UserPreferences
import com.yunemusic.service.ACTION_OPEN_CAR_MODE
import com.yunemusic.service.bluetoothDeviceAddress
import com.yunemusic.service.isCarModeDevice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.yunemusic.ui.navigation.YuneMusicNavHost
import com.yunemusic.ui.theme.YuneMusicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()

    @Inject lateinit var userPreferences: UserPreferences

    // true opens the car mode, false closes it. Buffered so the launch intent survives
    // until the NavHost starts collecting.
    private val carModeCommands = Channel<Boolean>(Channel.BUFFERED)

    // Leaving the car must not keep the screen on in a pocket, so the car mode closes
    // when the picked radio disconnects.
    private val carDisconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.bluetoothDeviceAddress() ?: return
            lifecycleScope.launch {
                if (isCarModeDevice(userPreferences, address)) carModeCommands.send(false)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handle result if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        if (savedInstanceState == null) handlePlaybackIntent(intent)
        ContextCompat.registerReceiver(
            this, carDisconnectReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED),
            ContextCompat.RECEIVER_EXPORTED
        )

        setContent {
            YuneMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    YuneMusicNavHost(playerViewModel, carModeCommands.receiveAsFlow())
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(carDisconnectReceiver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlaybackIntent(intent)
    }

    private fun handlePlaybackIntent(intent: Intent?) {
        when (intent?.action) {
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ->
                playerViewModel.playFromSearch(voiceSearchQuery(intent))
            ACTION_OPEN_CAR_MODE -> carModeCommands.trySend(true)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
