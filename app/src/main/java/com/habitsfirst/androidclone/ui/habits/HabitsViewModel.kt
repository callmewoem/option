package com.habitsfirst.androidclone.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.util.DateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HabitsUiState(
    val isLoading: Boolean = true,
    val gating: List<HabitProgress> = emptyList(),
    val tracked: List<HabitProgress> = emptyList(),
    /** [HabitProgress.isCompleted] here means "a slip was logged today", not "done". */
    val antihabits: List<HabitProgress> = emptyList(),
    val dayScores: Map<LocalDate, Float> = emptyMap(),
    val goldStarDates: Set<LocalDate> = emptySet(),
)

/**
 * Read-only: progress today (for the accent on each card) plus the heatmap. Doing a
 * habit happens on Home; this screen is for reviewing and managing the list, so tapping
 * a card here always opens it for editing -- see [HabitsScreen].
 */
@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val dayScores = MutableStateFlow<Map<LocalDate, Float>>(emptyMap())

    val uiState: StateFlow<HabitsUiState> = combine(
        habitRepository.observeTodayProgressByKind(HabitKind.GATING),
        habitRepository.observeTodayProgressByKind(HabitKind.TRACKED),
        habitRepository.observeTodayProgressByKind(HabitKind.ANTIHABIT),
        preferencesRepository.goldStarDates,
        dayScores,
    ) { gating, tracked, antihabits, goldStars, scores ->
        HabitsUiState(
            isLoading = false,
            gating = gating,
            tracked = tracked,
            antihabits = antihabits,
            dayScores = scores,
            goldStarDates = goldStars.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    init {
        refreshHeatmap()
    }

    fun refreshHeatmap() {
        viewModelScope.launch {
            val end = DateProvider.todayString()
            val start = DateProvider.toDateString(DateProvider.fromDateString(end).minusWeeks(HEATMAP_WEEKS))
            dayScores.value = habitRepository.getDayScoresInRange(start, end)
                .mapKeys { DateProvider.fromDateString(it.key) }
        }
    }

    companion object {
        private const val HEATMAP_WEEKS = 20L
    }
}
