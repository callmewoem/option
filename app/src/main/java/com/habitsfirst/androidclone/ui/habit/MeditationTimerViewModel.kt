package com.habitsfirst.androidclone.ui.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeditationTimerUiState(
    val habit: Habit? = null,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false,
    val alreadyCompletedMinutes: Int = 0,
) {
    val targetSeconds: Int get() = (habit?.targetValue ?: 0) * 60
    val progressFraction: Float
        get() = if (targetSeconds == 0) 0f else (elapsedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
    val isComplete: Boolean get() = targetSeconds > 0 && elapsedSeconds >= targetSeconds
}

@HiltViewModel
class MeditationTimerViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val habitId: Long = requireNotNull(savedStateHandle.get<String>(Screen.ARG_HABIT_ID)).toLong()

    private val _uiState = MutableStateFlow(MeditationTimerUiState())
    val uiState: StateFlow<MeditationTimerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        viewModelScope.launch {
            val habit = habitRepository.getHabit(habitId)
            val alreadyDoneMinutes = habitRepository.getProgressOnce(habitId)
            _uiState.value = _uiState.value.copy(
                habit = habit,
                alreadyCompletedMinutes = alreadyDoneMinutes,
                elapsedSeconds = alreadyDoneMinutes * 60,
            )
        }
    }

    fun onStartPause() {
        if (_uiState.value.isRunning) {
            pause()
        } else {
            start()
        }
    }

    private fun start() {
        if (_uiState.value.isComplete) return
        _uiState.value = _uiState.value.copy(isRunning = true)
        tickerJob = viewModelScope.launch {
            while (_uiState.value.isRunning && !_uiState.value.isComplete) {
                delay(1_000)
                val next = _uiState.value.elapsedSeconds + 1
                _uiState.value = _uiState.value.copy(elapsedSeconds = next)
                if (_uiState.value.isComplete) {
                    persistProgress()
                    _uiState.value = _uiState.value.copy(isRunning = false)
                }
            }
        }
    }

    private fun pause() {
        tickerJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false)
        viewModelScope.launch { persistProgress() }
    }

    fun onReset() {
        tickerJob?.cancel()
        _uiState.value = _uiState.value.copy(elapsedSeconds = 0, isRunning = false)
    }

    fun onMarkComplete() {
        viewModelScope.launch {
            val habit = _uiState.value.habit ?: return@launch
            habitRepository.setProgress(habitId, habit.targetValue, habit.targetValue)
            _uiState.value = _uiState.value.copy(elapsedSeconds = _uiState.value.targetSeconds)
        }
    }

    private suspend fun persistProgress() {
        val habit = _uiState.value.habit ?: return
        val minutes = _uiState.value.elapsedSeconds / 60
        habitRepository.setProgress(habitId, minutes, habit.targetValue)
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}
