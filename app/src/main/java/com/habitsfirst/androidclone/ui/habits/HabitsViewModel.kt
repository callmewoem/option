package com.habitsfirst.androidclone.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitCompletionStat
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.util.DateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** Average gating-completion fraction for one day of the week, across the stats window. */
data class DayOfWeekStat(val dayOfWeek: DayOfWeek, val averageFraction: Float)

data class HabitsUiState(
    val isLoading: Boolean = true,
    val dayScores: Map<LocalDate, Float> = emptyMap(),
    val goldStarDates: Set<LocalDate> = emptySet(),
    val completionStats: List<HabitCompletionStat> = emptyList(),
    val dayOfWeekStats: List<DayOfWeekStat> = emptyList(),
)

/**
 * Pure stats: the heatmap, completion rate by habit, and completion rate by day of
 * week. Managing the habit list (add/edit/delete) lives in Settings, not here --
 * doing a habit lives on Home. This screen has nothing to tap.
 */
@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val dayScores = MutableStateFlow<Map<LocalDate, Float>>(emptyMap())
    private val completionStats = MutableStateFlow<List<HabitCompletionStat>>(emptyList())
    private val isLoading = MutableStateFlow(true)

    val uiState: StateFlow<HabitsUiState> = combine(
        dayScores,
        preferencesRepository.goldStarDates,
        completionStats,
        isLoading,
    ) { scores, goldStars, stats, loading ->
        HabitsUiState(
            isLoading = loading,
            dayScores = scores,
            goldStarDates = goldStars.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
            completionStats = stats,
            dayOfWeekStats = DayOfWeek.values().map { day ->
                val values = scores.filterKeys { it.dayOfWeek == day }.values
                DayOfWeekStat(day, if (values.isEmpty()) 0f else values.average().toFloat())
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val end = DateProvider.todayString()
            val start = DateProvider.toDateString(DateProvider.fromDateString(end).minusWeeks(WINDOW_WEEKS))
            dayScores.value = habitRepository.getDayScoresInRange(start, end)
                .mapKeys { DateProvider.fromDateString(it.key) }
            completionStats.value = habitRepository.getHabitCompletionStats(start, end)
            isLoading.value = false
        }
    }

    companion object {
        private const val WINDOW_WEEKS = 20L
    }
}
