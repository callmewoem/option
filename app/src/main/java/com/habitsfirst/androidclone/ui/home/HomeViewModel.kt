package com.habitsfirst.androidclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.EaseInStatus
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.data.repository.PenaltyRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.ProofOfLifeRepository
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
import java.time.DayOfWeek
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
    val easeInStatus: EaseInStatus? = null,
    /** True when the check-in is enabled and the user hasn't confirmed it yet today. */
    val proofOfLifeDue: Boolean = false,
) {
    val completedCount: Int get() = gating.count { it.isCompleted }
    val totalCount: Int get() = gating.size
    val allDone: Boolean get() = totalCount > 0 && completedCount == totalCount
    val pendingTodos: List<Todo> get() = todos.filterNot { it.isDone }
}

/** The ease-in ramp's streak length and proof-of-life due-ness -- grouped only to fit combine()'s 5-flow cap. */
private data class HomeMiscState(val easeInStreakLength: Int, val proofOfLifeDue: Boolean)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val lootboxRepository: LootboxRepository,
    private val penaltyRepository: PenaltyRepository,
    private val todoRepository: TodoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val proofOfLifeRepository: ProofOfLifeRepository,
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

    private val miscFlow = combine(
        preferencesRepository.easeInStreakLength,
        proofOfLifeRepository.settings,
        proofOfLifeRepository.isConfirmedTodayFlow,
    ) { easeInStreakLength, proofOfLifeSettings, confirmedToday ->
        HomeMiscState(easeInStreakLength, proofOfLifeDue = proofOfLifeSettings.enabled && !confirmedToday)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        kindsFlow,
        blockedAppRepository.observeBlockedApps(),
        streakRefreshTrigger,
        todoRepository.observeForToday(),
        miscFlow,
    ) { (gating, tracked, antihabits), blockedApps, _, todos, misc ->
        HomeUiState(
            isLoading = false,
            gating = gating,
            tracked = tracked,
            antihabits = antihabits,
            todos = todos,
            blockedApps = blockedApps,
            streakDays = habitRepository.computeCurrentStreak(),
            easeInStatus = habitRepository.getEaseInStatus(misc.easeInStreakLength),
            proofOfLifeDue = misc.proofOfLifeDue,
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

    fun onAddTodo(title: String, repeatDays: Set<DayOfWeek> = emptySet()) {
        if (title.isBlank()) return
        viewModelScope.launch { todoRepository.addTodo(title, repeatDays = repeatDays) }
    }

    fun onToggleTodoDone(todo: Todo) {
        viewModelScope.launch { todoRepository.setDone(todo, !todo.isDone) }
    }

    fun onDeleteTodo(todo: Todo) {
        viewModelScope.launch { todoRepository.delete(todo) }
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
