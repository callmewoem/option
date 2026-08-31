package com.habitsfirst.androidclone.ui.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.InstalledApp
import com.habitsfirst.androidclone.ui.navigation.Screen
import com.habitsfirst.androidclone.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditHabitUiState(
    val habitId: Long = 0L,
    val name: String = "",
    val kind: HabitKind = HabitKind.GATING,
    val type: HabitType = HabitType.STEPS,
    val targetValue: Int = HabitType.STEPS.defaultTarget(),
    val targetPackageName: String? = null,
    val targetAppLabel: String? = null,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val canDelete: Boolean = false,
    val installedApps: List<InstalledApp> = emptyList(),
    val savedSuccessfully: Boolean = false,
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
            (type != HabitType.APP_USAGE_MINUTES || targetPackageName != null) &&
            (type == HabitType.CUSTOM || targetValue > 0)
}

fun HabitType.defaultTarget(): Int = when (this) {
    HabitType.STEPS -> 10_000
    HabitType.EXERCISE_MINUTES -> 30
    HabitType.MEDITATION_MINUTES -> 10
    HabitType.APP_USAGE_MINUTES -> 15
    HabitType.CUSTOM -> 1
}

@HiltViewModel
class AddEditHabitViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val habitId: Long = savedStateHandle.get<String>(Screen.ARG_HABIT_ID)?.toLongOrNull() ?: 0L
    private val initialKind: HabitKind = savedStateHandle.get<String>(Screen.ARG_KIND)
        ?.let { runCatching { HabitKind.valueOf(it) }.getOrNull() }
        ?: HabitKind.GATING

    private val _uiState = MutableStateFlow(
        AddEditHabitUiState(
            habitId = habitId,
            kind = initialKind,
            isNew = habitId == 0L,
            canDelete = habitId != 0L,
        ),
    )
    val uiState: StateFlow<AddEditHabitUiState> = _uiState.asStateFlow()

    init {
        if (habitId != 0L) {
            viewModelScope.launch {
                habitRepository.getHabit(habitId)?.let { habit ->
                    _uiState.value = _uiState.value.copy(
                        name = habit.name,
                        kind = habit.kind,
                        type = habit.type,
                        targetValue = habit.targetValue,
                        targetPackageName = habit.targetPackageName,
                        targetAppLabel = habit.targetAppLabel,
                    )
                }
            }
        }
        viewModelScope.launch {
            val apps = installedAppsProvider.getLaunchableApps()
            _uiState.value = _uiState.value.copy(installedApps = apps)
        }
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onKindChanged(kind: HabitKind) {
        _uiState.value = _uiState.value.copy(kind = kind)
    }

    fun onTypeChanged(type: HabitType) {
        _uiState.value = _uiState.value.copy(
            type = type,
            targetValue = type.defaultTarget(),
            targetPackageName = if (type == HabitType.APP_USAGE_MINUTES) _uiState.value.targetPackageName else null,
            targetAppLabel = if (type == HabitType.APP_USAGE_MINUTES) _uiState.value.targetAppLabel else null,
        )
    }

    fun onTargetValueChanged(value: Int) {
        _uiState.value = _uiState.value.copy(targetValue = value.coerceAtLeast(1))
    }

    fun onTargetAppSelected(app: InstalledApp) {
        _uiState.value = _uiState.value.copy(targetPackageName = app.packageName, targetAppLabel = app.label)
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.isValid || state.isSaving) return
        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            habitRepository.saveHabit(
                Habit(
                    id = state.habitId,
                    name = state.name.trim(),
                    kind = state.kind,
                    type = state.type,
                    targetValue = if (state.type == HabitType.CUSTOM) 1 else state.targetValue,
                    targetPackageName = state.targetPackageName,
                    targetAppLabel = state.targetAppLabel,
                ),
            )
            _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
        }
    }

    fun onDelete() {
        if (habitId == 0L) return
        viewModelScope.launch {
            habitRepository.deleteHabit(habitId)
            _uiState.value = _uiState.value.copy(savedSuccessfully = true)
        }
    }
}
