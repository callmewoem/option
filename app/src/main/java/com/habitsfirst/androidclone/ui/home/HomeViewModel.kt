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
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.LootboxReward
import com.habitsfirst.androidclone.domain.model.Todo
import com.habitsfirst.androidclone.util.DateProvider
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
 * tracked and antihabit entries and this and tomorrow's todos, all completable
 * inline -- Habits and Todos are for managing lists, Home is for finishing them.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val gating: List<HabitProgress> = emptyList(),
    val tracked: List<HabitProgress> = emptyList(),
    val antihabits: List<HabitProgress> = emptyList(),
    /** Due today or tomorrow -- see [Todo]. */
    val todos: List<Todo> = emptyList(),
    val blockedApps: List<BlockedApp> = emptyList(),
    val streakDays: Int = 0,
    val easeInStatus: EaseInStatus? = null,
    /** True when the check-in is enabled and the user hasn't confirmed it yet today. */
    val proofOfLifeDue: Boolean = false,
    /** True until the first-run spotlight tour has been stepped through or dismissed. */
    val showTour: Boolean = false,
    /** True only on the same calendar day onboarding finished, until dismissed or a photo-verification habit exists. */
    val showPhotoVerificationPrompt: Boolean = false,
) {
    val completedCount: Int get() = gating.count { it.isCompleted }
    val totalCount: Int get() = gating.size
    val allDone: Boolean get() = totalCount > 0 && completedCount == totalCount
    val pendingTodos: List<Todo> get() = todos.filterNot { it.isDone }
}

/** The ease-in ramp's streak length, proof-of-life due-ness, tour visibility, and the photo-verification prompt's date/dismissal eligibility -- grouped only to fit combine()'s 5-flow cap. */
private data class HomeMiscState(
    val easeInStreakLength: Int,
    val proofOfLifeDue: Boolean,
    val showTour: Boolean,
    val photoVerificationPromptEligible: Boolean,
)

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

    /** Eligible only on the same calendar day onboarding finished and only until dismissed -- whether a photo-verification habit already exists is checked separately, in [uiState], since it needs the habit list rather than a preference. */
    private val photoVerificationPromptFlow = combine(
        preferencesRepository.onboardingCompletedDate,
        preferencesRepository.hasDismissedPhotoVerificationPrompt,
    ) { completedDate, dismissed -> !dismissed && completedDate == DateProvider.todayString() }

    private val miscFlow = combine(
        preferencesRepository.easeInStreakLength,
        proofOfLifeRepository.settings,
        proofOfLifeRepository.isConfirmedTodayFlow,
        preferencesRepository.hasSeenHomeTour,
        photoVerificationPromptFlow,
    ) { easeInStreakLength, proofOfLifeSettings, confirmedToday, hasSeenTour, photoPromptEligible ->
        HomeMiscState(
            easeInStreakLength,
            proofOfLifeDue = proofOfLifeSettings.enabled && !confirmedToday,
            showTour = !hasSeenTour,
            photoVerificationPromptEligible = photoPromptEligible,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        kindsFlow,
        blockedAppRepository.observeBlockedApps(),
        streakRefreshTrigger,
        todoRepository.observeUpcoming(),
        miscFlow,
    ) { (gating, tracked, antihabits), blockedApps, _, todos, misc ->
        val hasImageVerificationHabit =
            (gating + tracked + antihabits).any { it.habit.type == HabitType.CUSTOM && it.habit.requiresPhotoVerification }
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
            showTour = misc.showTour,
            showPhotoVerificationPrompt = misc.photoVerificationPromptEligible && !hasImageVerificationHabit,
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

    fun onAddTodo(title: String, dueTomorrow: Boolean = false) {
        if (title.isBlank()) return
        viewModelScope.launch { todoRepository.addTodo(title, dueTomorrow = dueTomorrow) }
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

    /** Called once the tour is stepped through to its end or skipped -- never shown again. */
    fun onTourDismissed() {
        viewModelScope.launch { preferencesRepository.setHasSeenHomeTour(true) }
    }

    /** Called on both an explicit dismiss and on tapping through to set one up -- either way, no need to keep nudging. */
    fun onPhotoVerificationPromptDismissed() {
        viewModelScope.launch { preferencesRepository.setHasDismissedPhotoVerificationPrompt(true) }
    }

    private suspend fun maybeAwardLootbox() {
        val allComplete = habitRepository.areAllHabitsCompletedForDate()
        val reward = lootboxRepository.maybeAwardDailyLootbox(allComplete)
        if (reward != null) _wonReward.value = reward
    }
}
