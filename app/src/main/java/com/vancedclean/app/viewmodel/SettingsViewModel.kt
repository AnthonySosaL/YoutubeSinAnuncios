package com.vancedclean.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vancedclean.app.data.AppPreferences
import com.vancedclean.app.data.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences.getInstance(application)

    val settings: StateFlow<AppSettings> = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun setDarkMode(value: Int) {
        viewModelScope.launch { preferences.setDarkMode(value) }
    }

    fun setDefaultQuality(value: String) {
        viewModelScope.launch { preferences.setDefaultQuality(value) }
    }

    fun setBackgroundPlayback(value: Boolean) {
        viewModelScope.launch { preferences.setBackgroundPlayback(value) }
    }

    fun setAutoplay(value: Boolean) {
        viewModelScope.launch { preferences.setAutoplay(value) }
    }

    fun setCountry(value: String) {
        viewModelScope.launch { preferences.setCountry(value) }
    }
}
