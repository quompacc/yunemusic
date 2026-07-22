package com.yunemusic.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.yunemusic.data.preferences.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lauscht auf eingehende Benachrichtigungen anderer Apps und senkt kurzzeitig
 * die Musiklautstärke ("Ducking"), damit z.B. Nachrichtentöne hörbar sind.
 *
 * Benötigt die Benachrichtigungszugriff-Berechtigung (Einstellungen →
 * Benachrichtigungszugriff), die über die Settings-Seite angefragt wird.
 */
@AndroidEntryPoint
class NotificationDuckService : NotificationListenerService() {

    @Inject lateinit var duckingController: DuckingController
    @Inject lateinit var userPreferences: UserPreferences

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Eigene Notifications (Player-Notification!) niemals ducken
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return

        // Dauerhafte/stille Notifications lösen kein Ducking aus:
        // Ongoing (Musikplayer, Navigation), Gruppen-Zusammenfassungen
        if (sbn.isOngoing) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        serviceScope.launch {
            if (userPreferences.duckOnNotification.first()) {
                duckingController.requestDuck()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
