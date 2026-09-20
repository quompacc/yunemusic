package com.yunemusic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunemusic.data.preferences.UserPreferences
import com.yunemusic.service.TasteAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val audioQuality: Int = 1,
    val wifiOnly: Boolean = false,
    val prefetchEnabled: Boolean = true,
    val carModeTrigger: String = "manual",
    val loudnessLimiter: Boolean = true,
    val duckOnNotification: Boolean = true,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val tasteAnalyzer: TasteAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.prefetchEnabled.collect { enabled ->
                _uiState.update { it.copy(prefetchEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            combine(
                userPreferences.audioQuality,
                userPreferences.wifiOnly,
                userPreferences.carModeTrigger,
                userPreferences.loudnessLimiter,
                userPreferences.duckOnNotification
            ) { quality, wifiOnly, trigger, limiter, duck ->
                Prefs(quality, wifiOnly, trigger, limiter, duck)
            }.collect { prefs ->
                // copy() statt Komplett-Zuweisung, damit eine sichtbare message
                // nicht von Preference-Emissionen weggewischt wird
                _uiState.update {
                    it.copy(
                        audioQuality = prefs.quality,
                        wifiOnly = prefs.wifiOnly,
                        carModeTrigger = prefs.trigger,
                        loudnessLimiter = prefs.limiter,
                        duckOnNotification = prefs.duck
                    )
                }
            }
        }
    }

    private data class Prefs(
        val quality: Int,
        val wifiOnly: Boolean,
        val trigger: String,
        val limiter: Boolean,
        val duck: Boolean
    )

    fun updateAudioQuality(quality: Int) {
        viewModelScope.launch { userPreferences.setAudioQuality(quality) }
    }

    fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setWifiOnly(enabled) }
    }

    fun updatePrefetchEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setPrefetchEnabled(enabled) }
    }

    fun updateCarModeTrigger(trigger: String) {
        viewModelScope.launch { userPreferences.setCarModeTrigger(trigger) }
    }

    fun updateLoudnessLimiter(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setLoudnessLimiter(enabled) }
    }

    fun updateDuckOnNotification(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDuckOnNotification(enabled) }
    }

    fun resetTasteProfile() {
        viewModelScope.launch {
            tasteAnalyzer.resetProfile()
            showMessage("Taste Profile wurde zurückgesetzt")
        }
    }

    private fun showMessage(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(message = message, isError = isError) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.update { it.copy(message = null) }
        }
    }
}
