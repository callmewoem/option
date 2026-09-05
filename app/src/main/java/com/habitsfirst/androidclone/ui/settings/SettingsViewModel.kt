package com.habitsfirst.androidclone.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.healthconnect.HealthConnectManager
import com.habitsfirst.androidclone.data.repository.BedtimeRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.LimitedUnblockRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.ProofOfLifeRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.ThemeCodeResult
import com.habitsfirst.androidclone.domain.model.ThemeVariant
import com.habitsfirst.androidclone.service.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class SettingsUiState(
    val habits: List<Habit> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val anthropicApiKey: String? = null,
    val selectedThemeVariant: ThemeVariant = ThemeVariant.DEFAULT,
    val unlockedThemeVariants: Set<ThemeVariant> = setOf(ThemeVariant.DEFAULT),
    val graceTokenCount: Int = 0,
    val taskSkipTokenCount: Int = 0,
    val bedtimeEnabled: Boolean = false,
    val bedtimeStart: String = "22:30",
    val bedtimeEnd: String = "06:30",
    val morningReminderEnabled: Boolean = true,
    val morningReminderTime: String = "08:00",
    val proofOfLifeEnabled: Boolean = false,
    val proofOfLifeTime: String = "08:00",
    val proofOfLifeWindowMinutes: Int = PreferencesRepository.DEFAULT_PROOF_OF_LIFE_WINDOW_MINUTES,
    val weeklyDigestEnabled: Boolean = false,
    val weeklyDigestDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    val weeklyDigestTime: String = "18:00",
    val hardModeEnabled: Boolean = false,
    val hardModeToggleLockedUntilEpochMillis: Long = 0L,
    val limitedUnblockEnabled: Boolean = false,
    val easeInStreakLength: Int = PreferencesRepository.DEFAULT_EASE_IN_STREAK_LENGTH,
    /** False on any device without the Health Connect provider installed -- the whole section hides then. */
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermissionsGranted: Boolean = false,
    val healthConnectSyncEnabled: Boolean = false,
)

private data class ThemeAndTokens(
    val selectedVariant: ThemeVariant,
    val unlockedIds: Set<String>,
    val graceTokens: Int,
    val taskSkipTokens: Int,
)

/** Bedtime, the morning todo reminder, the morning proof-of-life check-in, and the weekly digest -- the settings screen's notification-scheduling rows. */
private data class ReminderSettings(
    val bedtime: PreferencesRepository.BedtimeSettings,
    val morning: PreferencesRepository.MorningReminderSettings,
    val proofOfLife: PreferencesRepository.ProofOfLifeSettings,
    val weeklyDigest: PreferencesRepository.WeeklyDigestSettings,
)

/** Hard mode's on/off state paired with its toggle-cooldown expiry -- split out only to keep [extraSettings] within combine()'s 5-flow cap. */
private data class HardModeState(
    val enabled: Boolean,
    val toggleLockedUntilEpochMillis: Long,
)

/** Hard mode and limited unblocking -- the two blocking-behavior toggles -- grouped only to keep [extraSettings] within combine()'s 5-flow cap. */
private data class BlockingSettings(
    val hardMode: HardModeState,
    val limitedUnblockEnabled: Boolean,
)

/** Hard mode/limited unblocking, the ease-in ramp's streak length, the photo-verification API key, and Health Connect sync -- grouped only to fit combine()'s 5-flow cap. */
private data class ExtraSettings(
    val blocking: BlockingSettings,
    val easeInStreakLength: Int,
    val anthropicApiKey: String?,
    val healthConnectSyncEnabled: Boolean,
    val healthConnectPermissionsGranted: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
    private val bedtimeRepository: BedtimeRepository,
    private val lootboxRepository: LootboxRepository,
    private val proofOfLifeRepository: ProofOfLifeRepository,
    private val limitedUnblockRepository: LimitedUnblockRepository,
    private val healthConnectManager: HealthConnectManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** A live permission check, not a stored preference -- refreshed via [refreshHealthConnectPermissions]. */
    private val _healthConnectPermissionsGranted = MutableStateFlow(false)

    /** One-shot feedback for the last theme-code redemption attempt; cleared by [onThemeCodeMessageShown] once shown. */
    private val _themeCodeMessage = MutableStateFlow<String?>(null)
    val themeCodeMessage: StateFlow<String?> = _themeCodeMessage.asStateFlow()

    init {
        refreshHealthConnectPermissions()
    }

    // kotlinx.coroutines.flow.combine's typed overloads top out at 5 flows, so the
    // theme/token, reminder, and hard-mode/ease-in/API-key/health-connect groups are
    // paired up first, then combined at the end.
    private val themeAndTokens = combine(
        preferencesRepository.selectedThemeVariantId.map { ThemeVariant.fromId(it) },
        preferencesRepository.unlockedThemeVariantIds,
        lootboxRepository.graceTokenCount,
        lootboxRepository.taskSkipTokenCount,
    ) { selectedVariant, unlockedIds, grace, taskSkip ->
        ThemeAndTokens(selectedVariant, unlockedIds, grace, taskSkip)
    }

    private val reminderSettings = combine(
        bedtimeRepository.settings,
        preferencesRepository.morningTodoReminderSettings,
        proofOfLifeRepository.settings,
        preferencesRepository.weeklyDigestSettings,
        ::ReminderSettings,
    )

    private val hardModeState = combine(
        preferencesRepository.isHardModeEnabled,
        preferencesRepository.hardModeToggleLockedUntilEpochMillis,
        ::HardModeState,
    )

    private val blockingSettings = combine(
        hardModeState,
        limitedUnblockRepository.isEnabled,
        ::BlockingSettings,
    )

    private val extraSettings = combine(
        blockingSettings,
        preferencesRepository.easeInStreakLength,
        preferencesRepository.anthropicApiKey,
        preferencesRepository.isHealthConnectSyncEnabled,
        _healthConnectPermissionsGranted,
        ::ExtraSettings,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        habitRepository.observeHabits(),
        preferencesRepository.areNotificationsEnabled,
        themeAndTokens,
        reminderSettings,
        extraSettings,
    ) { habits, notificationsEnabled, tt, rs, extra ->
        SettingsUiState(
            habits = habits,
            notificationsEnabled = notificationsEnabled,
            anthropicApiKey = extra.anthropicApiKey,
            selectedThemeVariant = tt.selectedVariant,
            unlockedThemeVariants = ThemeVariant.entries.filter { it.name in tt.unlockedIds }.toSet(),
            graceTokenCount = tt.graceTokens,
            taskSkipTokenCount = tt.taskSkipTokens,
            bedtimeEnabled = rs.bedtime.enabled,
            bedtimeStart = rs.bedtime.start,
            bedtimeEnd = rs.bedtime.end,
            morningReminderEnabled = rs.morning.enabled,
            morningReminderTime = rs.morning.time,
            proofOfLifeEnabled = rs.proofOfLife.enabled,
            proofOfLifeTime = rs.proofOfLife.time,
            proofOfLifeWindowMinutes = rs.proofOfLife.windowMinutes,
            weeklyDigestEnabled = rs.weeklyDigest.enabled,
            weeklyDigestDayOfWeek = rs.weeklyDigest.dayOfWeek,
            weeklyDigestTime = rs.weeklyDigest.time,
            hardModeEnabled = extra.blocking.hardMode.enabled,
            hardModeToggleLockedUntilEpochMillis = extra.blocking.hardMode.toggleLockedUntilEpochMillis,
            limitedUnblockEnabled = extra.blocking.limitedUnblockEnabled,
            easeInStreakLength = extra.easeInStreakLength,
            healthConnectAvailable = healthConnectManager.isAvailable,
            healthConnectPermissionsGranted = extra.healthConnectPermissionsGranted,
            healthConnectSyncEnabled = extra.healthConnectSyncEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onNotificationsToggled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotificationsEnabled(enabled) }
    }

    fun onAnthropicApiKeyChanged(key: String) {
        viewModelScope.launch { preferencesRepository.setAnthropicApiKey(key) }
    }

    fun onThemeVariantSelected(variant: ThemeVariant) {
        viewModelScope.launch {
            if (variant in uiState.value.unlockedThemeVariants) {
                preferencesRepository.setSelectedThemeVariantId(variant.name)
            }
        }
    }

    /** Redeems a theme code (see [com.habitsfirst.androidclone.domain.model.ThemeRedeemCode]); result surfaces via [themeCodeMessage]. */
    fun onRedeemThemeCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _themeCodeMessage.value = when (val result = lootboxRepository.redeemThemeCode(code)) {
                is ThemeCodeResult.Unlocked ->
                    "Unlocked ${result.variants.joinToString { it.displayName }}!"
                ThemeCodeResult.AlreadyUnlocked -> "Already unlocked -- nothing new from that code."
                ThemeCodeResult.Invalid -> "That code doesn't match anything."
            }
        }
    }

    fun onThemeCodeMessageShown() {
        _themeCodeMessage.value = null
    }

    fun onBedtimeChanged(enabled: Boolean, start: String, end: String) {
        viewModelScope.launch { bedtimeRepository.setBedtime(enabled, start, end) }
    }

    fun onMorningReminderChanged(enabled: Boolean, time: String) {
        viewModelScope.launch { preferencesRepository.setMorningTodoReminderSettings(enabled, time) }
    }

    fun onProofOfLifeChanged(enabled: Boolean, time: String, windowMinutes: Int) {
        viewModelScope.launch { proofOfLifeRepository.setProofOfLife(enabled, time, windowMinutes) }
    }

    /**
     * Opt-in "N/7 days complete, best streak M days" recap -- schedules/cancels
     * [com.habitsfirst.androidclone.service.WeeklyDigestWorker] to match, same as
     * [onHealthConnectSyncToggled]. [SettingsScreen] calls this on every day-of-week chip
     * tap and every keystroke of the time field (not just the enable switch), so the
     * WorkManager call is only made when [enabled] actually flips -- otherwise typing a
     * time would touch WorkManager's work database on every keystroke for no reason.
     */
    fun onWeeklyDigestChanged(enabled: Boolean, dayOfWeek: DayOfWeek, time: String) {
        viewModelScope.launch {
            val wasEnabled = preferencesRepository.weeklyDigestSettings.first().enabled
            preferencesRepository.setWeeklyDigestSettings(enabled, dayOfWeek, time)
            if (enabled == wasEnabled) return@launch
            if (enabled) {
                WorkScheduler.scheduleWeeklyDigest(appContext)
            } else {
                WorkScheduler.cancelWeeklyDigest(appContext)
            }
        }
    }

    /**
     * Enabling grants a batch of grace tokens to ease into it; either direction is a no-op while the previous
     * toggle's cooldown is still active (see [PreferencesRepository.setHardModeEnabled]). The switch itself is
     * disabled in [SettingsScreen] during the cooldown, so this is normally unreachable then anyway.
     */
    fun onHardModeToggled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHardModeEnabled(enabled) }
    }

    /** Once habits are done, blocked apps and sites re-lock after [LimitedUnblockRepository.WINDOW_MINUTES] instead of staying open the rest of the day. */
    fun onLimitedUnblockToggled(enabled: Boolean) {
        viewModelScope.launch { limitedUnblockRepository.setEnabled(enabled) }
    }

    fun onEaseInStreakLengthChanged(days: Int) {
        viewModelScope.launch { preferencesRepository.setEaseInStreakLength(days) }
    }

    /** Consumes a task-skip token to force-complete [habitId] today without doing it. */
    fun onSkipHabitToday(habitId: Long, targetValue: Int) {
        viewModelScope.launch {
            if (lootboxRepository.consumeTaskSkipToken()) {
                habitRepository.setProgress(habitId, targetValue, targetValue)
            }
        }
    }

    /** Re-checks whether the Health Connect read permissions are actually granted -- call on screen resume, since a grant/revoke happens outside the app. */
    fun refreshHealthConnectPermissions() {
        viewModelScope.launch { _healthConnectPermissionsGranted.value = healthConnectManager.hasPermissions() }
    }

    /** Callback for the permission request launcher in [SettingsScreen] -- doesn't turn sync on by itself, that's still the separate toggle below. */
    fun onHealthConnectPermissionResult(allGranted: Boolean) {
        _healthConnectPermissionsGranted.value = allGranted
    }

    fun onHealthConnectSyncToggled(enabled: Boolean) {
        viewModelScope.launch {
            val allowed = enabled && healthConnectManager.hasPermissions()
            preferencesRepository.setHealthConnectSyncEnabled(allowed)
            if (allowed) {
                WorkScheduler.scheduleHealthConnectSync(appContext)
            } else {
                WorkScheduler.cancelHealthConnectSync(appContext)
            }
        }
    }
}
