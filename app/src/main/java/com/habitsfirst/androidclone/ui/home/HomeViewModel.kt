package com.habitsfirst.androidclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.data.repository.PenaltyRepository
import com.habitsfirst.androidclone.data.repository.TodoRepository
import com.habitsfirst.androidclone.domain.model.BlockedApp
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.LootboxReward
import com.habitsfirst.androidclone.domain.model.Todo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything doable in one sitting, first thing in the morning: today's gating,
 * tracked and antihabit entries and today's todos, all completable inline -- Habits
 * and Todos are for managing lists, Home is for finishing them.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val gating: List<HabitProgress> = emptyList(),
    val tracked: List<HabitProgress> = emptyList(),
    val antihabits: List<HabitProgress> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val blockedApps: List<BlockedApp> = emptyList(),
    val streakDays: Int = 0,
) {
    val completedCount: Int get() = gating.count { it.isCompleted }
    val totalCount: Int get() = gating.size
    val allDone: Boolean get() = totalCount > 0 && completedCount == totalCount
    val pendingTodos: List<Todo> get() = todos.filterNot { it.isDone }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val lootboxRepository: LootboxRepository,
    private val penaltyRepository: PenaltyRepository,
    private val todoRepository: TodoRepository,
) : ViewModel() {

    /** Bumped whenever a completion changes, so the streak (which needs a DB round trip) recomputes. */
    private val streakRefreshTrigger = MutableStateFlow(0)

    private val _wonReward = MutableStateFlow<LootboxReward?>(null)
    val wonReward: StateFlow<LootboxReward?> = _wonReward

    // Paired first since kotlinx.coroutines.flow.combine tops out at 5 flows.
    private val kindsFlow = combine(
        habitRepository.observeTodayProgressByKind(HabitKind.GATING),
        habitRepository.observeTodayProgressByKind(HabitKind.TRACKED),
        habitRepository.observeTodayProgressByKind(HabitKind.ANTIHABIT),
        ::Triple,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        kindsFlow,
        blockedAppRepository.observeBlockedApps(),
        streakRefreshTrigger,
        todoRepository.observeForDate(),
    ) { (gating, tracked, antihabits), blockedApps, _, todos ->
        HomeUiState(
            isLoading = false,
            gating = gating,
            tracked = tracked,
            antihabits = antihabits,
            todos = todos,
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
            maybeAwardLootbox()
        }
    }

    fun onLogProgress(habitId: Long, target: Int, newValue: Int) {
        viewModelScope.launch {
            habitRepository.setProgress(habitId, newValue, target)
            streakRefreshTrigger.value++
            maybeAwardLootbox()
        }
    }

    fun onToggleAntihabitSlip(habitId: Long, habitName: String, logged: Boolean) {
        viewModelScope.launch {
            habitRepository.setAntihabitSlipLogged(habitId, logged)
            if (logged) penaltyRepository.applyAntihabitSlipPenalty(habitName)
            streakRefreshTrigger.value++
        }
    }

    fun onAddTodo(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { todoRepository.addTodo(title) }
    }

    fun onToggleTodoDone(todo: Todo) {
        viewModelScope.launch { todoRepository.setDone(todo.id, !todo.isDone) }
    }

    fun refreshStreak() {
        streakRefreshTrigger.value++
    }

    fun onRewardDismissed() {
        _wonReward.value = null
    }

    private suspend fun maybeAwardLootbox() {
        val allComplete = habitRepository.areAllHabitsCompletedForDate()
        val reward = lootboxRepository.maybeAwardDailyLootbox(allComplete)
        if (reward != null) _wonReward.value = reward
    }
}
