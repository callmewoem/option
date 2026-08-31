package com.habitsfirst.androidclone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BedtimeRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
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
    val selectedThemeVariant: ThemeVariant = ThemeVariant.DEFAULT,
    val unlockedThemeVariants: Set<ThemeVariant> = setOf(ThemeVariant.DEFAULT),
    val graceTokenCount: Int = 0,
    val taskSkipTokenCount: Int = 0,
    val bedtimeEnabled: Boolean = false,
    val bedtimeStart: String = "22:30",
    val bedtimeEnd: String = "06:30",
    val morningReminderEnabled: Boolean = true,
    val morningReminderTime: String = "08:00",
)

private data class ThemeAndTokens(
    val selectedVariant: ThemeVariant,
    val unlockedIds: Set<String>,
    val graceTokens: Int,
    val taskSkipTokens: Int,
)

private data class ReminderSettings(
    val bedtime: PreferencesRepository.BedtimeSettings,
    val morning: PreferencesRepository.MorningReminderSettings,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
    private val bedtimeRepository: BedtimeRepository,
    private val lootboxRepository: LootboxRepository,
) : ViewModel() {

    // kotlinx.coroutines.flow.combine's typed overloads top out at 5 flows, so the
    // theme/token and reminder groups are paired up first, then combined at the end.
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
        ::ReminderSettings,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        habitRepository.observeHabits(),
        preferencesRepository.areNotificationsEnabled,
        themeAndTokens,
        reminderSettings,
    ) { habits, notificationsEnabled, tt, rs ->
        SettingsUiState(
            habits = habits,
            notificationsEnabled = notificationsEnabled,
            selectedThemeVariant = tt.selectedVariant,
            unlockedThemeVariants = ThemeVariant.entries.filter { it.name in tt.unlockedIds }.toSet(),
            graceTokenCount = tt.graceTokens,
            taskSkipTokenCount = tt.taskSkipTokens,
            bedtimeEnabled = rs.bedtime.enabled,
            bedtimeStart = rs.bedtime.start,
            bedtimeEnd = rs.bedtime.end,
            morningReminderEnabled = rs.morning.enabled,
            morningReminderTime = rs.morning.time,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onNotificationsToggled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotificationsEnabled(enabled) }
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

    /** Consumes a task-skip token to force-complete [habitId] today without doing it. */
    fun onSkipHabitToday(habitId: Long, targetValue: Int) {
        viewModelScope.launch {
            if (lootboxRepository.consumeTaskSkipToken()) {
                habitRepository.setProgress(habitId, targetValue, targetValue)
            }
        }
    }
}
