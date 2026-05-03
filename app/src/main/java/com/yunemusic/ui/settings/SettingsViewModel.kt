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
    val carModeTrigger: String = "manual",
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
            combine(
                userPreferences.audioQuality,
                userPreferences.wifiOnly,
                userPreferences.carModeTrigger
            ) { quality, wifiOnly, trigger ->
                SettingsUiState(audioQuality = quality, wifiOnly = wifiOnly, carModeTrigger = trigger)
            }.collect { _uiState.value = it }
        }
    }

    fun updateAudioQuality(quality: Int) {
        viewModelScope.launch { userPreferences.setAudioQuality(quality) }
    }

    fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setWifiOnly(enabled) }
    }

    fun updateCarModeTrigger(trigger: String) {
        viewModelScope.launch { userPreferences.setCarModeTrigger(trigger) }
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
