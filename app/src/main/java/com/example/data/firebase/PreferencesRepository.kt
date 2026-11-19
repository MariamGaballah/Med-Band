package com.example.data.firebase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.medBandDataStore: DataStore<Preferences> by preferencesDataStore(name = "medband_prefs")

data class LocalAppPreferences(
    val languageCode: String = "ar",
    val darkTheme: Boolean = true,
    val autoReconnectBle: Boolean = true,
    val lastDeviceAddress: String = "",
    val lastDeviceName: String = "",
    val measurementIntervalSec: Int = 2,
    val fallSensitivity: String = "BALANCED"
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val LAST_DEVICE_MAC = stringPreferencesKey("last_device_mac")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val INTERVAL_SEC = intPreferencesKey("measurement_interval_sec")
        val FALL_SENSITIVITY = stringPreferencesKey("fall_sensitivity")
    }

    val preferencesFlow: Flow<LocalAppPreferences> = context.medBandDataStore.data.map { prefs ->
        LocalAppPreferences(
            languageCode = prefs[Keys.LANGUAGE] ?: "ar",
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            autoReconnectBle = prefs[Keys.AUTO_RECONNECT] ?: true,
            lastDeviceAddress = prefs[Keys.LAST_DEVICE_MAC] ?: "",
            lastDeviceName = prefs[Keys.LAST_DEVICE_NAME] ?: "",
            measurementIntervalSec = prefs[Keys.INTERVAL_SEC] ?: 2,
            fallSensitivity = prefs[Keys.FALL_SENSITIVITY] ?: "BALANCED"
        )
    }

    suspend fun setLanguage(code: String) {
        context.medBandDataStore.edit { it[Keys.LANGUAGE] = if (code == "en") "en" else "ar" }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.medBandDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.medBandDataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun saveLastDevice(address: String, name: String) {
        context.medBandDataStore.edit {
            it[Keys.LAST_DEVICE_MAC] = address
            it[Keys.LAST_DEVICE_NAME] = name
        }
    }

    suspend fun setMeasurementIntervalSec(seconds: Int) {
        context.medBandDataStore.edit { it[Keys.INTERVAL_SEC] = seconds.coerceIn(1, 30) }
    }

    suspend fun setFallSensitivity(mode: String) {
        context.medBandDataStore.edit { it[Keys.FALL_SENSITIVITY] = mode }
    }
}
