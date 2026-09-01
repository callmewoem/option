package com.habitsfirst.androidclone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.Habit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val habits: List<Habit> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val anthropicApiKey: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        habitRepository.observeHabits(),
        preferencesRepository.areNotificationsEnabled,
        preferencesRepository.anthropicApiKey,
    ) { habits, notificationsEnabled, apiKey ->
        SettingsUiState(habits = habits, notificationsEnabled = notificationsEnabled, anthropicApiKey = apiKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onNotificationsToggled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotificationsEnabled(enabled) }
    }

    fun onAnthropicApiKeyChanged(key: String) {
        viewModelScope.launch { preferencesRepository.setAnthropicApiKey(key) }
    }
}
