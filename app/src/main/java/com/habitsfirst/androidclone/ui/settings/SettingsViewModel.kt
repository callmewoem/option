package com.habitsfirst.androidclone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BedtimeRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.ProofOfLifeRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.ThemeVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val hardModeEnabled: Boolean = false,
    val easeInStreakLength: Int = PreferencesRepository.DEFAULT_EASE_IN_STREAK_LENGTH,
)

private data class ThemeAndTokens(
    val selectedVariant: ThemeVariant,
    val unlockedIds: Set<String>,
    val graceTokens: Int,
    val taskSkipTokens: Int,
)

/** Bedtime, the morning todo reminder, and the morning proof-of-life check-in -- everything keyed off "today's morning". */
private data class ReminderSettings(
    val bedtime: PreferencesRepository.BedtimeSettings,
    val morning: PreferencesRepository.MorningReminderSettings,
    val proofOfLife: PreferencesRepository.ProofOfLifeSettings,
)

/** Hard mode, the ease-in ramp's streak length, and the photo-verification API key -- grouped only to fit combine()'s 5-flow cap. */
private data class ExtraSettings(
    val hardModeEnabled: Boolean,
    val easeInStreakLength: Int,
    val anthropicApiKey: String?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
    private val bedtimeRepository: BedtimeRepository,
    private val lootboxRepository: LootboxRepository,
    private val proofOfLifeRepository: ProofOfLifeRepository,
) : ViewModel() {

    // kotlinx.coroutines.flow.combine's typed overloads top out at 5 flows, so the
    // theme/token, reminder, and hard-mode/ease-in/API-key groups are paired up first,
    // then combined at the end.
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
        ::ReminderSettings,
    )

    private val extraSettings = combine(
        preferencesRepository.isHardModeEnabled,
        preferencesRepository.easeInStreakLength,
        preferencesRepository.anthropicApiKey,
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
            hardModeEnabled = extra.hardModeEnabled,
            easeInStreakLength = extra.easeInStreakLength,
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

    fun onBedtimeChanged(enabled: Boolean, start: String, end: String) {
        viewModelScope.launch { bedtimeRepository.setBedtime(enabled, start, end) }
    }

    fun onMorningReminderChanged(enabled: Boolean, time: String) {
        viewModelScope.launch { preferencesRepository.setMorningTodoReminderSettings(enabled, time) }
    }

    fun onProofOfLifeChanged(enabled: Boolean, time: String, windowMinutes: Int) {
        viewModelScope.launch { proofOfLifeRepository.setProofOfLife(enabled, time, windowMinutes) }
    }

    /** Enabling grants a batch of grace tokens to ease into it (see [PreferencesRepository.setHardModeEnabled]). */
    fun onHardModeToggled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHardModeEnabled(enabled) }
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
}
