package com.habitsfirst.androidclone.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.InstalledApp
import com.habitsfirst.androidclone.service.WorkScheduler
import com.habitsfirst.androidclone.ui.habit.defaultTarget
import com.habitsfirst.androidclone.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One of the ready-made habits offered during onboarding. */
data class HabitTemplate(
    val name: String,
    val type: HabitType,
    val targetValue: Int = type.defaultTarget(),
)

val onboardingHabitTemplates = listOf(
    HabitTemplate("Walk 10,000 steps", HabitType.STEPS, 10_000),
    HabitTemplate("Work out", HabitType.EXERCISE_MINUTES, 30),
    HabitTemplate("Meditate", HabitType.MEDITATION_MINUTES, 10),
    HabitTemplate("Read", HabitType.CUSTOM),
    HabitTemplate("Make my bed", HabitType.CUSTOM),
)

data class OnboardingUiState(
    val installedApps: List<InstalledApp> = emptyList(),
    val selectedPackageNames: Set<String> = emptySet(),
    val selectedTemplates: Set<HabitTemplate> = setOf(onboardingHabitTemplates[0], onboardingHabitTemplates[2]),
    val isFinishing: Boolean = false,
    val finished: Boolean = false,
) {
    val canContinueFromApps: Boolean get() = true // blocking zero apps is a valid (if pointless) choice
    val canContinueFromHabits: Boolean get() = selectedTemplates.isNotEmpty()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val blockedAppRepository: BlockedAppRepository,
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = installedAppsProvider.getLaunchableApps()
            _uiState.value = _uiState.value.copy(installedApps = apps)
        }
    }

    fun onAppToggled(packageName: String, selected: Boolean) {
        val current = _uiState.value.selectedPackageNames
        _uiState.value = _uiState.value.copy(
            selectedPackageNames = if (selected) current + packageName else current - packageName,
        )
    }

    fun onTemplateToggled(template: HabitTemplate, selected: Boolean) {
        val current = _uiState.value.selectedTemplates
        _uiState.value = _uiState.value.copy(
            selectedTemplates = if (selected) current + template else current - template,
        )
    }

    fun finishOnboarding() {
        if (_uiState.value.isFinishing) return
        _uiState.value = _uiState.value.copy(isFinishing = true)
        viewModelScope.launch {
            val state = _uiState.value
            val appsByPackage = state.installedApps.associateBy { it.packageName }
            state.selectedPackageNames.forEach { packageName ->
                val label = appsByPackage[packageName]?.label ?: packageName
                blockedAppRepository.setBlocked(packageName, label, blocked = true)
            }
            state.selectedTemplates.forEach { template ->
                habitRepository.saveHabit(
                    Habit(name = template.name, type = template.type, targetValue = template.targetValue),
                )
            }
            preferencesRepository.setOnboardingComplete(true)
            WorkScheduler.scheduleUsageTracking(appContext)
            _uiState.value = _uiState.value.copy(isFinishing = false, finished = true)
        }
    }
}
