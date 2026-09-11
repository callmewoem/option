package com.locke.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locke.app.data.repository.ExperimentRepository
import com.locke.app.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashUiState(val isReady: Boolean = false, val onboardingComplete: Boolean = false)

/** Reads the onboarding flag once so [LockeNavHost] knows which start destination to use. */
@HiltViewModel
class SplashViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    experimentRepository: ExperimentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val onboardingComplete = preferencesRepository.isOnboardingComplete.first()
            _uiState.value = SplashUiState(isReady = true, onboardingComplete = onboardingComplete)
        }
        // Fire-and-forget, never gates isReady -- every typed getter on
        // ExperimentRepository already has an offline-safe default, so there's nothing
        // for the rest of the app to wait on here. Onboarding does its own refresh too
        // (it needs the result before its own paywall-step decision); this one keeps
        // an already-onboarded user's assignments current on every subsequent launch.
        viewModelScope.launch { experimentRepository.refreshIfNeeded() }
    }
}
