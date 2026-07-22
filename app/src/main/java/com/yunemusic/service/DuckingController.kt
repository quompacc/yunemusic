package com.yunemusic.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vermittelt zwischen [NotificationDuckService] (erkennt eingehende
 * Benachrichtigungen) und [MusicService] (senkt die Lautstärke).
 *
 * Hintergrund: Einfache Benachrichtigungstöne fordern auf den meisten Geräten
 * KEINEN Audio-Focus an — sie werden einfach über die Musik gemischt. Das
 * automatische Ducking von ExoPlayer greift daher nur bei Navigation/Assistant/
 * Anrufen. Für "leiser bei Nachrichten" braucht es diesen eigenen Mechanismus.
 */
@Singleton
class DuckingController @Inject constructor() {

    companion object {
        const val DUCK_VOLUME_FACTOR = 0.25f
        private const val DUCK_DURATION_MS = 2500L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var restoreJob: Job? = null

    private val _duckFactor = MutableStateFlow(1f)
    /** 1.0 = volle Lautstärke, [DUCK_VOLUME_FACTOR] während einer Benachrichtigung */
    val duckFactor: StateFlow<Float> = _duckFactor.asStateFlow()

    /** Senkt die Lautstärke kurzzeitig; mehrere Aufrufe verlängern die Duck-Phase. */
    fun requestDuck() {
        restoreJob?.cancel()
        _duckFactor.value = DUCK_VOLUME_FACTOR
        restoreJob = scope.launch {
            delay(DUCK_DURATION_MS)
            _duckFactor.value = 1f
        }
    }
}
