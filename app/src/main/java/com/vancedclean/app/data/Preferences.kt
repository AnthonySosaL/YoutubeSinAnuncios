package com.vancedclean.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

const val DARK_MODE_SYSTEM = 0
const val DARK_MODE_LIGHT = 1
const val DARK_MODE_DARK = 2

const val DEFAULT_QUALITY = "auto"
const val DEFAULT_COUNTRY = "US"

data class AppSettings(
    val darkMode: Int = DARK_MODE_SYSTEM,
    val defaultQuality: String = DEFAULT_QUALITY,
    val backgroundPlayback: Boolean = true,
    val country: String = DEFAULT_COUNTRY,
    val autoplay: Boolean = true,
)

/** Copia en memoria de los ajustes para leerlos sin corrutinas (reproductor, red). */
object CurrentSettings {
    @Volatile
    var value: AppSettings = AppSettings()
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vancedclean_settings")

class AppPreferences private constructor(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val darkMode: Flow<Int> = preferences.map { (it[DARK_MODE] ?: DARK_MODE_SYSTEM).coerceIn(DARK_MODE_SYSTEM, DARK_MODE_DARK) }

    val defaultQuality: Flow<String> = preferences.map { it[DEFAULT_QUALITY_KEY] ?: DEFAULT_QUALITY }

    val backgroundPlayback: Flow<Boolean> = preferences.map { it[BACKGROUND_PLAYBACK] ?: true }

    val country: Flow<String> = preferences.map { it[COUNTRY] ?: DEFAULT_COUNTRY }

    val settings: Flow<AppSettings> = preferences.map { prefs ->
        AppSettings(
            darkMode = (prefs[DARK_MODE] ?: DARK_MODE_SYSTEM).coerceIn(DARK_MODE_SYSTEM, DARK_MODE_DARK),
            defaultQuality = prefs[DEFAULT_QUALITY_KEY] ?: DEFAULT_QUALITY,
            backgroundPlayback = prefs[BACKGROUND_PLAYBACK] ?: true,
            country = prefs[COUNTRY] ?: DEFAULT_COUNTRY,
            autoplay = prefs[AUTOPLAY] ?: true,
        )
    }

    suspend fun setDarkMode(value: Int) {
        dataStore.edit { it[DARK_MODE] = value.coerceIn(DARK_MODE_SYSTEM, DARK_MODE_DARK) }
    }

    suspend fun setDefaultQuality(value: String) {
        dataStore.edit { it[DEFAULT_QUALITY_KEY] = value }
    }

    suspend fun setBackgroundPlayback(value: Boolean) {
        dataStore.edit { it[BACKGROUND_PLAYBACK] = value }
    }

    suspend fun setCountry(value: String) {
        dataStore.edit { it[COUNTRY] = value }
    }

    suspend fun setAutoplay(value: Boolean) {
        dataStore.edit { it[AUTOPLAY] = value }
    }

    companion object {
        private val AUTOPLAY = booleanPreferencesKey("autoplay")
        private val DARK_MODE = intPreferencesKey("dark_mode")
        private val DEFAULT_QUALITY_KEY = stringPreferencesKey("default_quality")
        private val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        private val COUNTRY = stringPreferencesKey("country")

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
    }
}
