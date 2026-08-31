package com.habitsfirst.androidclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.domain.model.BlockedApp
import com.habitsfirst.androidclone.domain.model.HabitProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val habitProgress: List<HabitProgress> = emptyList(),
    val blockedApps: List<BlockedApp> = emptyList(),
    val streakDays: Int = 0,
) {
    val completedCount: Int get() = habitProgress.count { it.isCompleted }
    val totalCount: Int get() = habitProgress.size
    val allDone: Boolean get() = totalCount > 0 && completedCount == totalCount
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val blockedAppRepository: BlockedAppRepository,
) : ViewModel() {

    /** Bumped whenever a completion changes, so the streak (which needs a DB round trip) recomputes. */
    private val streakRefreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = combine(
        habitRepository.observeTodayProgress(),
        blockedAppRepository.observeBlockedApps(),
        streakRefreshTrigger,
    ) { progress, blockedApps, _ ->
        HomeUiState(
            isLoading = false,
            habitProgress = progress,
            blockedApps = blockedApps,
            streakDays = habitRepository.computeCurrentStreak(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun onCustomHabitToggled(habitId: Long, done: Boolean) {
        viewModelScope.launch {
            habitRepository.setCustomHabitDone(habitId, done)
            streakRefreshTrigger.value++
        }
    }

    fun onLogProgress(habitId: Long, target: Int, newValue: Int) {
        viewModelScope.launch {
            habitRepository.setProgress(habitId, newValue, target)
            streakRefreshTrigger.value++
        }
    }

    fun refreshStreak() {
        streakRefreshTrigger.value++
    }
}
