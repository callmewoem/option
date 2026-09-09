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
import com.habitsfirst.androidclone.data.repository.LockoutRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.data.repository.PenaltyRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.ProofOfLifeRepository
import com.habitsfirst.androidclone.domain.model.BlockedApp
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.LootboxReward
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
 * tracked and antihabit entries, all completable inline. Managing habit lists lives in
 * Settings; the plain task list lives on its own screen (design spec §9) -- Today is
 * for finishing things, not managing them.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val gating: List<HabitProgress> = emptyList(),
    val tracked: List<HabitProgress> = emptyList(),
    val antihabits: List<HabitProgress> = emptyList(),
    val blockedApps: List<BlockedApp> = emptyList(),
    val streakDays: Int = 0,
    val easeInStatus: EaseInStatus? = null,
    /** True once the check-in window is open and unconfirmed -- see [ProofOfLifeRepository.isDueFlow]. */
    val proofOfLifeDue: Boolean = false,
    /** The configured check-in time ("HH:mm") -- the window opens here. */
    val proofOfLifeTime: String = "08:00",
    /** Minutes after [proofOfLifeTime] before a missed check-in is penalized -- the window's actual deadline. */
    val proofOfLifeWindowMinutes: Int = PreferencesRepository.DEFAULT_PROOF_OF_LIFE_WINDOW_MINUTES,
    /** True until the first-run spotlight tour has been stepped through or dismissed. */
    val showTour: Boolean = false,
    /** True only on the same calendar day onboarding finished, until dismissed or a photo-verification habit exists. */
    val showPhotoVerificationPrompt: Boolean = false,
    /** True while the app-usage/Health-Connect one-off refresh kicked off on app open/resume (or the manual refresh button) is still running. */
    val isRefreshingDataDrivenHabits: Boolean = false,
    /** How many times a blocked app/URL was actually covered by the block screen today -- an impulse-control signal (see [BlockAttemptRepository]), shown as a small chip only when non-zero. */
    val blockedOpenAttemptsToday: Int = 0,
    /** 0 when no self-lockout is running; otherwise the instant it ends -- see [LockoutRepository]. */
    val lockoutUntilEpochMillis: Long = 0L,
) {
    val completedCount: Int get() = gating.count { it.isCompleted }
    val totalCount: Int get() = gating.size
    val allDone: Boolean get() = totalCount > 0 && completedCount == totalCount
}

/** The check-in due-ness, its configured time and window -- grouped so [miscFlow] stays within combine()'s 5-flow cap while still carrying enough for the Today countdown banner (design spec §8). */
private data class ProofOfLifeMisc(
    val due: Boolean,
    val time: String,
    val windowMinutes: Int,
)

/** The ease-in ramp's streak length, proof-of-life due-ness/deadline, tour visibility, the photo-verification prompt's date/dismissal eligibility, and whether the data-driven-habit refresh is in flight -- grouped only to fit combine()'s 5-flow cap. */
private data class HomeMiscState(
    val easeInStreakLength: Int,
    val proofOfLife: ProofOfLifeMisc,
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
    private val lockoutRepository: LockoutRepository,
    private val penaltyRepository: PenaltyRepository,
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

    private val proofOfLifeMiscFlow = combine(
        proofOfLifeRepository.isDueFlow,
        proofOfLifeRepository.settings,
    ) { due, settings -> ProofOfLifeMisc(due, settings.time, settings.windowMinutes) }

    private val miscFlow = combine(
        preferencesRepository.easeInStreakLength,
        proofOfLifeMiscFlow,
        preferencesRepository.hasSeenHomeTour,
        photoVerificationPromptFlow,
        isRefreshingFlow,
    ) { easeInStreakLength, proofOfLife, hasSeenTour, photoPromptEligible, isRefreshing ->
        HomeMiscState(
            easeInStreakLength,
            proofOfLife = proofOfLife,
            showTour = !hasSeenTour,
            photoVerificationPromptEligible = photoPromptEligible,
            isRefreshingDataDrivenHabits = isRefreshing,
        )
    }

    // blockedOpenAttemptsTodayFlow is combined separately (rather than as a 6th flow
    // here, past kotlinx.coroutines.flow.combine's 5-flow cap) and filled in below.
    private val baseUiState = combine(
        kindsFlow,
        blockedAppRepository.observeBlockedApps(),
        streakRefreshTrigger,
        miscFlow,
        lockoutRepository.lockoutUntilEpochMillis,
    ) { (gating, tracked, antihabits), blockedApps, _, misc, lockoutUntil ->
        val hasImageVerificationHabit =
            (gating + tracked + antihabits).any { it.habit.type == HabitType.PHOTO }
        HomeUiState(
            isLoading = false,
            gating = gating,
            tracked = tracked,
            antihabits = antihabits,
            blockedApps = blockedApps,
            streakDays = habitRepository.computeCurrentStreak(),
            easeInStatus = habitRepository.getEaseInStatus(misc.easeInStreakLength),
            proofOfLifeDue = misc.proofOfLife.due,
            proofOfLifeTime = misc.proofOfLife.time,
            proofOfLifeWindowMinutes = misc.proofOfLife.windowMinutes,
            showTour = misc.showTour,
            showPhotoVerificationPrompt = misc.photoVerificationPromptEligible && !hasImageVerificationHabit,
            isRefreshingDataDrivenHabits = misc.isRefreshingDataDrivenHabits,
            lockoutUntilEpochMillis = lockoutUntil,
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
        // app -- catch it up right away rather than waiting. HomeScreen also re-triggers
        // this on every ON_RESUME, since this init block only runs once per ViewModel
        // (i.e. it alone would miss a background/foreground cycle).
        refreshDataDrivenHabits()
    }

    /** Kicks off an immediate refresh of app-usage and (if enabled) Health-Connect-backed habit progress, instead of waiting for their periodic workers. Called on app open and every resume (see [init] and HomeScreen's ON_RESUME effect) and from Home's manual refresh action. */
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

    /** Starts (or restarts) a self-lockout -- see [LockoutRepository]. Called from [LockoutDialog]. */
    fun onStartLockout(minutes: Int) {
        viewModelScope.launch { lockoutRepository.startLockout(minutes) }
    }

    /** Ends an active lockout early -- only reachable from here, Home itself; see [LockoutRepository]. */
    fun onCancelLockout() {
        viewModelScope.launch { lockoutRepository.cancelLockout() }
    }

    private suspend fun maybeAwardLootbox() {
        val allComplete = habitRepository.areAllHabitsCompletedForDate()
        val reward = lootboxRepository.maybeAwardDailyLootbox(allComplete)
        if (reward != null) _wonReward.value = reward
    }
}
