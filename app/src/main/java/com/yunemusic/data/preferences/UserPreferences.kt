package com.yunemusic.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yune_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_AUDIO_QUALITY = intPreferencesKey("audio_quality") // 0=128, 1=256, 2=320
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_CAR_MODE_TRIGGER = stringPreferencesKey("car_mode_trigger") // bluetooth, manual
        val KEY_TASTE_PROFILE_JSON = stringPreferencesKey("taste_profile_json")
        val KEY_LOUDNESS_LIMITER = booleanPreferencesKey("loudness_limiter")
        val KEY_DUCK_ON_NOTIFICATION = booleanPreferencesKey("duck_on_notification")
    }

    val audioQuality: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs -> prefs[KEY_AUDIO_QUALITY] ?: 1 }

    val wifiOnly: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs -> prefs[KEY_WIFI_ONLY] ?: false }

    val carModeTrigger: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs -> prefs[KEY_CAR_MODE_TRIGGER] ?: "manual" }

    val loudnessLimiter: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs -> prefs[KEY_LOUDNESS_LIMITER] ?: true }

    val duckOnNotification: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs -> prefs[KEY_DUCK_ON_NOTIFICATION] ?: true }

    val tasteProfileJson: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs -> prefs[KEY_TASTE_PROFILE_JSON] ?: "" }

    suspend fun setAudioQuality(quality: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUDIO_QUALITY] = quality
        }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun setCarModeTrigger(trigger: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CAR_MODE_TRIGGER] = trigger
        }
    }

    suspend fun setLoudnessLimiter(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOUDNESS_LIMITER] = enabled
        }
    }

    suspend fun setDuckOnNotification(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DUCK_ON_NOTIFICATION] = enabled
        }
    }

    suspend fun setTasteProfileJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TASTE_PROFILE_JSON] = json
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
