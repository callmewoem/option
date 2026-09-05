package com.habitsfirst.androidclone.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.habitsfirst.androidclone.data.repository.BlockAttemptRepository
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
import com.habitsfirst.androidclone.service.HealthConnectSyncWorker
import com.habitsfirst.androidclone.service.UsageTrackingWorker
import com.habitsfirst.androidclone.service.WorkScheduler
import com.habitsfirst.androidclone.util.DateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
    /** True while the app-usage/Health-Connect one-off refresh kicked off on app open (or the manual refresh button) is still running. */
    val isRefreshingDataDrivenHabits: Boolean = false,
    /** How many times a blocked app/URL was actually covered by the block screen today -- an impulse-control signal (see [BlockAttemptRepository]), shown as a small chip only when non-zero. */
    val blockedOpenAttemptsToday: Int = 0,
) {
    val completedCount: Int get() = gating.count { it.isCompleted }
    val totalCount: Int get() = gating.size
    val allDone: Boolean get() = totalCount > 0 && completedCount == totalCount
    val pendingTodos: List<Todo> get() = todos.filterNot { it.isDone }
}

/** The ease-in ramp's streak length, proof-of-life due-ness, tour visibility, the photo-verification prompt's date/dismissal eligibility, and whether the data-driven-habit refresh is in flight -- grouped only to fit combine()'s 5-flow cap. */
private data class HomeMiscState(
    val easeInStreakLength: Int,
    val proofOfLifeDue: Boolean,
    val showTour: Boolean,
    val photoVerificationPromptEligible: Boolean,
    val isRefreshingDataDrivenHabits: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val blockAttemptRepository: BlockAttemptRepository,
    private val lootboxRepository: LootboxRepository,
    private val penaltyRepository: PenaltyRepository,
    private val todoRepository: TodoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val proofOfLifeRepository: ProofOfLifeRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** Bumped whenever a completion changes, so the streak (which needs a DB round trip) recomputes. */
    private val streakRefreshTrigger = MutableStateFlow(0)

    private val _wonReward = MutableStateFlow<LootboxReward?>(null)
    val wonReward: StateFlow<LootboxReward?> = _wonReward

    /**
     * Whether either of the one-off refreshes kicked off by [refreshDataDrivenHabits] is
     * still enqueued or running, so Home can show a spinner instead of a refresh that looks
     * like it did nothing -- the actual progress numbers arrive separately, once the worker
     * writes them and [kindsFlow] picks up the change.
     */
    private val isRefreshingFlow = combine(
        WorkManager.getInstance(appContext).getWorkInfosForUniqueWorkFlow(UsageTrackingWorker.ONE_OFF_NAME),
        WorkManager.getInstance(appContext).getWorkInfosForUniqueWorkFlow(HealthConnectSyncWorker.ONE_OFF_NAME),
    ) { usageWork, healthConnectWork ->
        (usageWork + healthConnectWork).any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    }

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

    /** Re-derives "today" reactively (see [DateProvider.currentDateFlow]) and updates the instant [BlockAttemptRepository] logs a new attempt, rather than waiting for an unrelated flow to re-emit -- see [uiState]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val blockedOpenAttemptsTodayFlow = DateProvider.currentDateFlow()
        .flatMapLatest { date -> blockAttemptRepository.observeAttemptCountForDate(date) }

    private val miscFlow = combine(
        preferencesRepository.easeInStreakLength,
        proofOfLifeRepository.isDueFlow,
        preferencesRepository.hasSeenHomeTour,
        photoVerificationPromptFlow,
        isRefreshingFlow,
    ) { easeInStreakLength, proofOfLifeDue, hasSeenTour, photoPromptEligible, isRefreshing ->
        HomeMiscState(
            easeInStreakLength,
            proofOfLifeDue = proofOfLifeDue,
            showTour = !hasSeenTour,
            photoVerificationPromptEligible = photoPromptEligible,
            isRefreshingDataDrivenHabits = isRefreshing,
        )
    }

    // blockedOpenAttemptsTodayFlow is combined separately (rather than as a 6th flow
    // here, past kotlinx.coroutines.flow.combine's 5-flow cap) and filled in below --
    // see that flow's doc for why it needs its own live subscription instead of being
    // just another one-shot suspend read alongside the rest of this lambda.
    private val baseUiState = combine(
        kindsFlow,
        blockedAppRepository.observeBlockedApps(),
        streakRefreshTrigger,
        todoRepository.observeUpcoming(),
        miscFlow,
    ) { (gating, tracked, antihabits), blockedApps, _, todos, misc ->
        val hasImageVerificationHabit =
            (gating + tracked + antihabits).any { it.habit.type == HabitType.PHOTO }
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
            isRefreshingDataDrivenHabits = misc.isRefreshingDataDrivenHabits,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseUiState,
        blockedOpenAttemptsTodayFlow,
    ) { base, attemptsToday -> base.copy(blockedOpenAttemptsToday = attemptsToday) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    init {
        // Data-driven progress (app usage, Health Connect) otherwise only updates on the
        // next 15/30-min periodic tick, so it can be stale first thing after opening the
        // app -- catch it up right away rather than waiting.
        refreshDataDrivenHabits()
    }

    /** Kicks off an immediate refresh of app-usage and (if enabled) Health-Connect-backed habit progress, instead of waiting for their periodic workers. Called on app open (see [init]) and from Home's manual refresh action. */
    fun refreshDataDrivenHabits() {
        viewModelScope.launch {
            WorkScheduler.requestUsageRefreshNow(appContext)
            if (preferencesRepository.isHealthConnectSyncEnabled.first()) {
                WorkScheduler.requestHealthConnectRefreshNow(appContext)
            }
        }
    }

    fun onTallyHabitToggled(habitId: Long, done: Boolean) {
        viewModelScope.launch {
            habitRepository.setTallyHabitDone(habitId, done)
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
