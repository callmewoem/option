package com.habitsfirst.androidclone.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.local.dao.ScarReasonCount
import com.habitsfirst.androidclone.data.repository.BlockAttemptRepository
import com.habitsfirst.androidclone.data.repository.CompletionTimeDistribution
import com.habitsfirst.androidclone.data.repository.ConsistencyStats
import com.habitsfirst.androidclone.data.repository.HabitCompletionStat
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.TodoRepository
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

/** How far back the stats window looks. [weeks] drives every query; [label] is the chip text. */
enum class StatsRange(val weeks: Long, val label: String) {
    FOUR_WEEKS(4, "4w"),
    TWELVE_WEEKS(12, "12w"),
    TWENTY_WEEKS(20, "20w"),
    YEAR(52, "1y"),
}

data class HabitsUiState(
    val isLoading: Boolean = true,
    val range: StatsRange = StatsRange.TWENTY_WEEKS,
    val dayScores: Map<LocalDate, Float> = emptyMap(),
    val goldStarDates: Set<LocalDate> = emptySet(),
    val scarredDates: Set<LocalDate> = emptySet(),
    val completionStats: List<HabitCompletionStat> = emptyList(),
    val dayOfWeekStats: List<DayOfWeekStat> = emptyList(),
    /** Current unbroken streak of fully-complete days, same figure Home shows -- not bounded by [range]. */
    val currentStreak: Int = 0,
    /** Longest run of 100%-complete days found within [range]. */
    val longestStreakInRange: Int = 0,
    /** Count of 100%-complete days within [range]. */
    val perfectDaysInRange: Int = 0,
    /** How many times the block screen actually covered a blocked app/URL within [range] -- an impulsivity signal. See [BlockAttemptRepository]. */
    val blockedOpenAttemptsInRange: Int = 0,
    /** Average minutes from creating a todo to completing it, within [range]. Null with no completed todos in range. */
    val averageTodoCompletionMinutes: Float? = null,
    /** When habit completions actually happen during the day, within [range] -- flags an "always at the last minute" pattern. */
    val completionTimeDistribution: CompletionTimeDistribution = CompletionTimeDistribution(emptyMap(), null),
    /** Day-to-day variance of the completion fraction, within [range] -- a variance-aware companion to the plain average. */
    val consistencyStats: ConsistencyStats = ConsistencyStats(0f, 0f, 0),
    /** How often each broken-streak reason shows up within [range], most frequent first. */
    val streakBreakReasons: List<ScarReasonCount> = emptyList(),
) {
    val availableRanges: List<StatsRange> get() = StatsRange.entries
}

/** The heatmap/day-score data, paired with completion stats, loading state, and the perfect-day count derived from the same fetch -- split out only to keep [uiState]'s combine() within its 5-flow cap. */
private data class ScoreData(
    val dayScores: Map<LocalDate, Float>,
    val completionStats: List<HabitCompletionStat>,
    val isLoading: Boolean,
    val perfectDaysInRange: Int,
)

/** Everything else the stats screen needs: gold stars, the selected range, streaks, and broken-streak dates. */
private data class MetaData(
    val goldStarDates: Set<LocalDate>,
    val range: StatsRange,
    val currentStreak: Int,
    val scarredDates: Set<LocalDate>,
    val longestStreakInRange: Int,
)

/** The newer ADHD-focused stats -- impulse control, completion timing, and consistency -- split out only to keep [HabitsViewModel.uiState]'s combine() within its arity cap. */
private data class InsightsData(
    val blockedOpenAttemptsInRange: Int,
    val averageTodoCompletionMinutes: Float?,
    val completionTimeDistribution: CompletionTimeDistribution,
    val consistencyStats: ConsistencyStats,
    val streakBreakReasons: List<ScarReasonCount>,
)

/**
 * Pure stats: the heatmap, streak summary, completion rate by habit, and completion
 * rate by day of week, over a selectable window. Managing the habit list (add/edit/
 * delete) lives in Settings, not here -- doing a habit lives on Home. This screen has
 * nothing to tap besides the range selector.
 */
@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
    private val blockAttemptRepository: BlockAttemptRepository,
    private val todoRepository: TodoRepository,
) : ViewModel() {

    private val dayScores = MutableStateFlow<Map<LocalDate, Float>>(emptyMap())
    private val completionStats = MutableStateFlow<List<HabitCompletionStat>>(emptyList())
    private val isLoading = MutableStateFlow(true)
    private val selectedRange = MutableStateFlow(StatsRange.TWENTY_WEEKS)
    private val currentStreak = MutableStateFlow(0)
    private val scarredDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    private val longestStreakInRange = MutableStateFlow(0)
    private val perfectDaysInRange = MutableStateFlow(0)
    private val blockedOpenAttemptsInRange = MutableStateFlow(0)
    private val averageTodoCompletionMinutes = MutableStateFlow<Float?>(null)
    private val completionTimeDistribution = MutableStateFlow(CompletionTimeDistribution(emptyMap(), null))
    private val consistencyStats = MutableStateFlow(ConsistencyStats(0f, 0f, 0))
    private val streakBreakReasons = MutableStateFlow<List<ScarReasonCount>>(emptyList())

    private val scoreData = combine(dayScores, completionStats, isLoading, perfectDaysInRange, ::ScoreData)

    private val metaData = combine(
        preferencesRepository.goldStarDates,
        selectedRange,
        currentStreak,
        scarredDates,
        longestStreakInRange,
    ) { goldStars, range, streak, scarred, longest ->
        MetaData(
            goldStarDates = goldStars.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
            range = range,
            currentStreak = streak,
            scarredDates = scarred,
            longestStreakInRange = longest,
        )
    }

    private val insightsData = combine(
        blockedOpenAttemptsInRange,
        averageTodoCompletionMinutes,
        completionTimeDistribution,
        consistencyStats,
        streakBreakReasons,
        ::InsightsData,
    )

    val uiState: StateFlow<HabitsUiState> = combine(scoreData, metaData, insightsData) { sd, md, id ->
        HabitsUiState(
            isLoading = sd.isLoading,
            range = md.range,
            dayScores = sd.dayScores,
            goldStarDates = md.goldStarDates,
            scarredDates = md.scarredDates,
            completionStats = sd.completionStats,
            dayOfWeekStats = DayOfWeek.values().map { day ->
                val values = sd.dayScores.filterKeys { it.dayOfWeek == day }.values
                DayOfWeekStat(day, if (values.isEmpty()) 0f else values.average().toFloat())
            },
            currentStreak = md.currentStreak,
            longestStreakInRange = md.longestStreakInRange,
            perfectDaysInRange = sd.perfectDaysInRange,
            blockedOpenAttemptsInRange = id.blockedOpenAttemptsInRange,
            averageTodoCompletionMinutes = id.averageTodoCompletionMinutes,
            completionTimeDistribution = id.completionTimeDistribution,
            consistencyStats = id.consistencyStats,
            streakBreakReasons = id.streakBreakReasons,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    init {
        // Re-runs whenever the calendar date actually changes, not just once at
        // startup -- otherwise this screen left open across midnight would keep
        // showing the window as it stood the moment it was opened.
        viewModelScope.launch {
            DateProvider.currentDateFlow().collect { refreshStats() }
        }
    }

    fun onRangeSelected(range: StatsRange) {
        if (range == selectedRange.value) return
        selectedRange.value = range
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val range = selectedRange.value
            val end = DateProvider.todayString()
            val start = DateProvider.toDateString(DateProvider.fromDateString(end).minusWeeks(range.weeks))
            val rawScores = habitRepository.getDayScoresInRange(start, end)
            val scores = rawScores.mapKeys { DateProvider.fromDateString(it.key) }
            dayScores.value = scores
            completionStats.value = habitRepository.getHabitCompletionStats(start, end)
            scarredDates.value = habitRepository.getScarredDatesInRange(start, end)
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            currentStreak.value = habitRepository.computeCurrentStreak()
            longestStreakInRange.value = longestRun(scores, DateProvider.fromDateString(start), DateProvider.fromDateString(end))
            perfectDaysInRange.value = scores.values.count { it >= 1f }
            blockedOpenAttemptsInRange.value = blockAttemptRepository.getAttemptCountInRange(start, end)
            averageTodoCompletionMinutes.value = todoRepository.getAverageCompletionMinutes(start, end)
            completionTimeDistribution.value = habitRepository.getCompletionTimeDistribution(start, end)
            consistencyStats.value = habitRepository.consistencyStatsForDayScores(rawScores, start, end)
            streakBreakReasons.value = habitRepository.getStreakBreakReasonBreakdown(start, end)
            isLoading.value = false
        }
    }

    /** Longest run of consecutive 100%-scored days between [start] and [end] inclusive. A date missing from [scores] (no activity logged) counts as incomplete, same as the heatmap's empty cell. */
    private fun longestRun(scores: Map<LocalDate, Float>, start: LocalDate, end: LocalDate): Int {
        var longest = 0
        var current = 0
        var cursor = start
        while (!cursor.isAfter(end)) {
            if ((scores[cursor] ?: 0f) >= 1f) {
                current++
                longest = maxOf(longest, current)
            } else {
                current = 0
            }
            cursor = cursor.plusDays(1)
        }
        return longest
    }
}
