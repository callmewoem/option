package com.habitsfirst.androidclone.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashUiState(val isReady: Boolean = false, val onboardingComplete: Boolean = false)

/** Reads the onboarding flag once so [HabitsFirstNavHost] knows which start destination to use. */
@HiltViewModel
class SplashViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val onboardingComplete = preferencesRepository.isOnboardingComplete.first()
            _uiState.value = SplashUiState(isReady = true, onboardingComplete = onboardingComplete)
        }
    }
}
