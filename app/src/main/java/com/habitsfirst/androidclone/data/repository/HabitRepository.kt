package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.HabitCompletionDao
import com.habitsfirst.androidclone.data.local.dao.HabitDao
import com.habitsfirst.androidclone.data.local.dao.ScarReasonCount
import com.habitsfirst.androidclone.data.local.dao.StreakScarDao
import com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity
import com.habitsfirst.androidclone.data.local.entity.toDomain
import com.habitsfirst.androidclone.data.local.entity.toEntity
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * A habit's completion rate over a stats window. For an ANTIHABIT, [rate] is the
 * *clean* rate (days without a logged slip) -- the inverse of the raw completed-entry
 * count, since a completion row there means a slip, not a done day. [completedCount]
 * and [totalDays] are the raw counts behind [rate] (still slip count / scheduled days
 * for an ANTIHABIT, not yet inverted) -- e.g. for a "12/14" label alongside the bar.
 */
data class HabitCompletionStat(val habit: Habit, val rate: Float, val completedCount: Int, val totalDays: Int)

/** A broken-streak date paired with why it broke -- see [com.habitsfirst.androidclone.data.local.entity.StreakScarEntity]. */
data class StreakScarInfo(val date: String, val reason: String)

/** Where an onboarding "ease into it" ramp currently stands -- see [Habit.easeInOrder]. */
data class EaseInStatus(
    val activeHabitName: String,
    val activeHabitStreak: Int,
    val requiredStreak: Int,
    val nextHabitName: String,
)

/** A coarse bucket of the day, for [HabitRepository.getCompletionTimeDistribution]. */
enum class TimeOfDayBucket(val label: String) {
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening"),
    NIGHT("Night"),
}

/**
 * When completions actually happen during the day, across every habit in range --
 * flags an "always finishing at the last minute" pattern (a [NIGHT]-heavy
 * distribution, or a high [averageMinutesSinceMidnight]) that a bare completion rate
 * can't show.
 */
data class CompletionTimeDistribution(
    val bucketCounts: Map<TimeOfDayBucket, Int>,
    /** Null with no timestamped completions in range. */
    val averageMinutesSinceMidnight: Float?,
) {
    val totalCount: Int get() = bucketCounts.values.sum()
}

/**
 * Day-to-day variance of [HabitRepository.getDayScoresInRange]'s completion fraction,
 * over the same window -- a low [standardDeviation] alongside a middling
 * [meanFraction] reads as "steady, room to grow"; a high one reads as "on-again,
 * off-again", which a bare average can't distinguish and is the more useful signal
 * for ADHD self-review.
 */
data class ConsistencyStats(
    val meanFraction: Float,
    val standardDeviation: Float,
    val daysCounted: Int,
)

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao,
    private val streakScarDao: StreakScarDao,
) {
    fun observeHabits(): Flow<List<Habit>> =
        habitDao.observeActiveHabits().map { list -> list.map { it.toDomain() } }

    fun observeHabitsByKind(kind: HabitKind): Flow<List<Habit>> =
        habitDao.observeActiveHabitsByKind(kind.name).map { list -> list.map { it.toDomain() } }

    fun observeHabitCount(): Flow<Int> = habitDao.observeActiveHabitCount()

    /** Today's GATING habits paired with today's progress, in display order -- what Home and the block screen show. */
    fun observeTodayProgress(): Flow<List<HabitProgress>> = observeTodayProgressByKind(HabitKind.GATING)

    // Re-derives "today" on every emission from currentDateFlow() rather than
    // capturing it once -- see that function's doc for why. Both of these Flows are
    // typically held for a whole ViewModel's lifetime (Home, the block gate), so
    // without this they'd go stale the first time someone leaves the screen open
    // across midnight.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayProgressByKind(kind: HabitKind): Flow<List<HabitProgress>> =
        DateProvider.currentDateFlow().flatMapLatest { today ->
            combine(
                habitDao.observeActiveHabitsByKindForDate(kind.name, dayBitFor(today)),
                completionDao.observeCompletionsForDate(today),
            ) { habits, completions ->
                val byHabitId = completions.associateBy { it.habitId }
                habits.map { habitEntity ->
                    val habit = habitEntity.toDomain()
                    val completion = byHabitId[habitEntity.id]
                    HabitProgress(
                        habit = habit,
                        currentValue = completion?.currentValue ?: 0,
                        isCompleted = completion?.isCompleted ?: false,
                    )
                }
            }
        }

    /** Emits true once every active GATING habit has a completed entry for today. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllHabitsCompletedToday(): Flow<Boolean> =
        DateProvider.currentDateFlow().flatMapLatest { today ->
            habitDao.observeIncompleteHabitCountForDate(today, dayBitFor(today)).map { it == 0 }
        }

    /** One-shot check used by the accessibility service before it locks the screen. */
    suspend fun areAllHabitsCompletedForDate(date: String = DateProvider.todayString()): Boolean =
        habitDao.getIncompleteHabitCountForDate(date, dayBitFor(date)) == 0

    suspend fun getHabit(id: Long): Habit? = habitDao.getById(id)?.toDomain()

    suspend fun saveHabit(habit: Habit): Long {
        // Preserve the existing row's position on edit -- callers (the add/edit form)
        // don't round-trip sortOrder through their UI state, so re-reading it from the
        // DB here avoids silently bumping an edited habit back to the top of the list.
        val sortOrder = if (habit.id == 0L) {
            (habitDao.getMaxSortOrder() ?: -1) + 1
        } else {
            habitDao.getById(habit.id)?.sortOrder ?: habit.sortOrder
        }
        return habitDao.upsert(habit.copy(sortOrder = sortOrder).toEntity())
    }

    suspend fun deleteHabit(habitId: Long) {
        habitDao.archive(habitId)
    }

    /** Removes expired makeup habits (see [PenaltyRepository]). Safe to call often -- it's a no-op most days. */
    suspend fun archiveExpiredHabits(date: String = DateProvider.todayString()) {
        habitDao.archiveExpiredHabits(date)
    }

    /** Sets a habit's raw progress value for today, marking it complete once it hits target. */
    suspend fun setProgress(habitId: Long, newValue: Int, target: Int, date: String = DateProvider.todayString()) {
        val clamped = newValue.coerceAtLeast(0)
        val existing = completionDao.getCompletion(habitId, date)
        val isCompleted = clamped >= target
        completionDao.upsert(
            HabitCompletionEntity(
                id = existing?.id ?: 0L,
                habitId = habitId,
                date = date,
                currentValue = clamped,
                isCompleted = isCompleted,
                completedAtEpochMillis = when {
                    isCompleted && existing?.isCompleted != true -> System.currentTimeMillis()
                    isCompleted -> existing?.completedAtEpochMillis
                    else -> null
                },
            ),
        )
    }

    /** Adds [delta] to a habit's current progress for today (used by app-usage tracking). */
    suspend fun addProgress(habitId: Long, delta: Int, target: Int, date: String = DateProvider.todayString()) {
        val existing = completionDao.getCompletion(habitId, date)
        setProgress(habitId, (existing?.currentValue ?: 0) + delta, target, date)
    }

    suspend fun setTallyHabitDone(habitId: Long, done: Boolean, date: String = DateProvider.todayString()) {
        setProgress(habitId, if (done) 1 else 0, target = 1, date = date)
    }

    /** Records a vision-model verdict on a submitted proof photo for a [HabitType.PHOTO] habit. */
    suspend fun setImageVerificationResult(
        habitId: Long,
        approved: Boolean,
        reasoning: String,
        imagePath: String?,
        date: String = DateProvider.todayString(),
    ) {
        val existing = completionDao.getCompletion(habitId, date)
        completionDao.upsert(
            HabitCompletionEntity(
                id = existing?.id ?: 0L,
                habitId = habitId,
                date = date,
                currentValue = if (approved) 1 else 0,
                isCompleted = approved,
                completedAtEpochMillis = when {
                    approved && existing?.isCompleted != true -> System.currentTimeMillis()
                    approved -> existing?.completedAtEpochMillis
                    else -> null
                },
                verificationImagePath = imagePath,
                verificationReasoning = reasoning,
            ),
        )
    }

    /**
     * Logs (or clears) a slip for an ANTIHABIT habit on [date]. Reuses the same
     * completion row as every other habit kind -- `isCompleted = true` means "a slip
     * was logged", not "done". UI showing antihabits inverts the usual green/red
     * mapping accordingly (see [HabitKind] docs).
     */
    suspend fun setAntihabitSlipLogged(habitId: Long, logged: Boolean, date: String = DateProvider.todayString()) {
        setProgress(habitId, if (logged) 1 else 0, target = 1, date = date)
    }

    /** Active habits that track time spent in a specific app, for the usage-tracking worker. */
    suspend fun getAppUsageHabitsOnce(): List<Habit> =
        habitDao.getActiveHabitsOnce()
            .map { it.toDomain() }
            .filter { it.type == HabitType.APP_USAGE_MINUTES && it.targetPackageName != null }

    /**
     * Active Health-Connect-backed habits (steps, workout minutes, sleep hours), for
     * [com.habitsfirst.androidclone.service.HealthConnectSyncWorker].
     */
    suspend fun getHealthConnectHabitsOnce(): List<Habit> =
        habitDao.getActiveHabitsOnce()
            .map { it.toDomain() }
            .filter { it.type in HEALTH_CONNECT_HABIT_TYPES }

    suspend fun getProgressOnce(habitId: Long, date: String = DateProvider.todayString()): Int =
        completionDao.getCompletion(habitId, date)?.currentValue ?: 0

    /**
     * Aggregate day score (0f..1f, the fraction of GATING habits completed) for every
     * date with recorded activity in the range -- the data source for the GitHub-style
     * heatmap. A date marked as a [com.habitsfirst.androidclone.data.local.entity.StreakScarEntity]
     * is forced to 0f regardless of how many habits were actually completed.
     */
    suspend fun getDayScoresInRange(startDate: String, endDate: String): Map<String, Float> {
        val counts = completionDao.getDayCompletionCountsInRange(startDate, endDate)
        val scarredDates = streakScarDao.getScarredDatesInRange(startDate, endDate).toSet()
        return counts.associate { c ->
            val score = if (c.totalCount == 0) 0f else c.completedCount.toFloat() / c.totalCount
            c.date to if (c.date in scarredDates) 0f else score
        }
    }

    /**
     * Every active habit's completion rate within [startDate]..[endDate] -- the data
     * source for the stats screen's per-habit distribution. A habit created partway
     * through the window is rated only over the days it actually existed, so a brand
     * new habit doesn't read as a mostly-missed one.
     */
    suspend fun getHabitCompletionStats(startDate: String, endDate: String): List<HabitCompletionStat> {
        val habits = habitDao.getActiveHabitsOnce().map { it.toDomain() }
        val countsByHabit = completionDao.getCompletedCountsByHabitInRange(startDate, endDate).associateBy { it.habitId }
        val rangeStart = DateProvider.fromDateString(startDate)
        val rangeEnd = DateProvider.fromDateString(endDate)
        return habits.map { habit ->
            val createdDate = Instant.ofEpochMilli(habit.createdAtEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val effectiveStart = maxOf(rangeStart, createdDate)
            // For a habit scheduled on only some days of the week, "total days" is how
            // many of those days actually fell in the window it existed for, not every
            // calendar day -- otherwise a Sunday-only habit completed every Sunday
            // would read as a mostly-missed one instead of a perfect one.
            val totalDays = if (effectiveStart > rangeEnd) 0 else countScheduledDays(habit, effectiveStart, rangeEnd)
            val completed = countsByHabit[habit.id]?.completedCount ?: 0
            val rawRate = if (totalDays <= 0) 0f else (completed.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
            val rate = if (habit.kind == HabitKind.ANTIHABIT) 1f - rawRate else rawRate
            HabitCompletionStat(habit, rate, completedCount = completed, totalDays = totalDays.toInt())
        }
    }

    private fun countScheduledDays(habit: Habit, start: LocalDate, end: LocalDate): Long {
        if (habit.isDaily) return ChronoUnit.DAYS.between(start, end) + 1
        var count = 0L
        var cursor = start
        while (!cursor.isAfter(end)) {
            if (cursor.dayOfWeek in habit.scheduledDays) count++
            cursor = cursor.plusDays(1)
        }
        return count
    }

    /**
     * Dates in range where [habitId] has a completed entry -- for GATING/TRACKED habits
     * that's a "green day"; for ANTIHABIT habits it's a "slip day" and callers should
     * invert the color mapping.
     */
    suspend fun getCompletedDatesForHabit(habitId: Long, startDate: String, endDate: String): Set<String> =
        completionDao.getCompletedDatesForHabit(habitId, startDate, endDate).toSet()

    /** Dates in range marked as a broken streak (see [com.habitsfirst.androidclone.data.local.entity.StreakScarEntity]) -- for the stats screen's "broken streaks" count. */
    suspend fun getScarredDatesInRange(startDate: String, endDate: String): Set<String> =
        streakScarDao.getScarredDatesInRange(startDate, endDate).toSet()

    /** How often each [com.habitsfirst.androidclone.data.local.entity.StreakScarEntity.reason] shows up in range, most frequent first -- a "why did streaks break" summary. */
    suspend fun getStreakBreakReasonBreakdown(startDate: String, endDate: String): List<ScarReasonCount> =
        streakScarDao.getReasonCountsInRange(startDate, endDate)

    /** See [CompletionTimeDistribution]. Buckets every habit-completion timestamp in range by time of day. */
    suspend fun getCompletionTimeDistribution(startDate: String, endDate: String): CompletionTimeDistribution =
        completionTimeDistributionOf(completionDao.getCompletionTimestampsInRange(startDate, endDate))

    /** See [ConsistencyStats]. Fetches [getDayScoresInRange] itself -- if a caller already has that map (e.g. [HabitsViewModel][com.habitsfirst.androidclone.ui.habits.HabitsViewModel] building the same window's heatmap), call [consistencyStatsForDayScores] directly instead to avoid querying it twice. */
    suspend fun getConsistencyStats(startDate: String, endDate: String): ConsistencyStats =
        consistencyStatsForDayScores(getDayScoresInRange(startDate, endDate), startDate, endDate)

    /**
     * See [ConsistencyStats]. Re-walks every calendar day in the range (not just the
     * ones [dayScores] happens to have a row for) so a day with zero logged activity
     * counts as a 0f, not a gap -- otherwise a habit tracked only sporadically would
     * understate its own variance. Takes an already-fetched [getDayScoresInRange] map
     * rather than fetching it again -- see [getConsistencyStats].
     */
    fun consistencyStatsForDayScores(dayScores: Map<String, Float>, startDate: String, endDate: String): ConsistencyStats {
        val start = DateProvider.fromDateString(startDate)
        val end = DateProvider.fromDateString(endDate)
        val fractions = mutableListOf<Float>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            fractions += dayScores[DateProvider.toDateString(cursor)] ?: 0f
            cursor = cursor.plusDays(1)
        }
        return consistencyStatsOf(fractions)
    }

    /** Same dates as [getScarredDatesInRange], paired with why each streak broke -- for [com.habitsfirst.androidclone.util.StatsExportUtil]'s data export. */
    suspend fun getStreakScarsInRange(startDate: String, endDate: String): List<StreakScarInfo> =
        streakScarDao.getScarsInRange(startDate, endDate).map { StreakScarInfo(it.date, it.reason) }

    // -- Onboarding "ease into it" ramp (see Habit.easeInOrder / EaseInRepository) -------

    /** Active habits chosen together at onboarding to ease in, easiest (order 0) first. */
    suspend fun getEaseInHabitsOnce(): List<Habit> =
        habitDao.getActiveHabitsOnce()
            .map { it.toDomain() }
            .filter { it.easeInOrder != null }
            .sortedBy { it.easeInOrder }

    suspend fun promoteHabitToGating(habitId: Long) {
        val habit = habitDao.getById(habitId)?.toDomain() ?: return
        habitDao.update(habit.copy(kind = HabitKind.GATING).toEntity())
    }

    /**
     * Current unbroken streak of completed days for a single habit, ending today or
     * yesterday (an in-progress today doesn't break yesterday's streak) -- the ease-in
     * ramp's graduation signal, as opposed to [computeCurrentStreak]'s all-gating-habits
     * version.
     */
    suspend fun computeHabitStreak(habitId: Long, lookbackDays: Int): Int {
        val today = DateProvider.fromDateString(DateProvider.todayString())
        val windowStart = today.minusDays(lookbackDays.toLong() + 1)
        val completedDates = getCompletedDatesForHabit(habitId, DateProvider.toDateString(windowStart), DateProvider.toDateString(today))

        var streak = 0
        var cursor = today
        if (DateProvider.toDateString(cursor) !in completedDates) cursor = cursor.minusDays(1)
        while (DateProvider.toDateString(cursor) in completedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /**
     * Null once there's no ease-in ramp in progress (none was started, or every chosen
     * habit has already graduated to GATING).
     */
    suspend fun getEaseInStatus(requiredStreak: Int): EaseInStatus? {
        val easeInHabits = getEaseInHabitsOnce()
        val activeGate = easeInHabits.filter { it.kind == HabitKind.GATING }.maxByOrNull { it.easeInOrder!! } ?: return null
        val next = easeInHabits.getOrNull(easeInHabits.indexOf(activeGate) + 1) ?: return null
        return EaseInStatus(
            activeHabitName = activeGate.name,
            activeHabitStreak = computeHabitStreak(activeGate.id, requiredStreak),
            requiredStreak = requiredStreak,
            nextHabitName = next.name,
        )
    }

    /** Current unbroken streak of days where every active GATING habit was completed, ending today or yesterday. */
    suspend fun computeCurrentStreak(): Int {
        var streak = 0
        var cursor = DateProvider.fromDateString(DateProvider.todayString())
        val today = cursor

        // If today isn't fully complete yet, start counting from yesterday instead,
        // so an in-progress day doesn't break yesterday's streak.
        if (!isDateFullyComplete(DateProvider.toDateString(cursor))) {
            cursor = cursor.minusDays(1)
        }

        while (true) {
            val dateStr = DateProvider.toDateString(cursor)
            if (isDateFullyComplete(dateStr)) {
                streak++
                cursor = cursor.minusDays(1)
            } else {
                break
            }
            // Safety bound so a data bug can't spin forever.
            if (today.minusDays(streak.toLong() + 400) > cursor) break
        }
        return streak
    }

    /**
     * A day only counts as "fully complete" once every GATING habit due on it has a
     * completed entry -- with day-of-week scheduling, that can be vacuously true on a
     * day nothing's due (e.g. the six days between a "hoover every Sunday" habit's
     * occurrences), so [getActiveGatingHabitCount] guards against the truly-empty case
     * (no GATING habit configured at all yet) trivially counting as complete instead.
     */
    private suspend fun isDateFullyComplete(date: String): Boolean {
        if (streakScarDao.isScarred(date)) return false
        val incompleteCount = habitDao.getIncompleteHabitCountForDate(date, dayBitFor(date))
        return incompleteCount == 0 && habitDao.getActiveGatingHabitCount() > 0
    }

    /** `1 shl (dayOfWeek.value - 1)` for [date]'s day of week -- see [HabitEntity.scheduledDaysMask][com.habitsfirst.androidclone.data.local.entity.HabitEntity]. */
    private fun dayBitFor(date: String): Int = 1 shl (DateProvider.fromDateString(date).dayOfWeek.value - 1)

    companion object {
        /** Habit types that [com.habitsfirst.androidclone.service.HealthConnectSyncWorker] can sync. */
        private val HEALTH_CONNECT_HABIT_TYPES = setOf(HabitType.STEPS, HabitType.WORKOUT_MINUTES, HabitType.SLEEP_HOURS)

        /** Pure so it's unit-testable without a DB -- see [getCompletionTimeDistribution]. */
        fun completionTimeDistributionOf(timestamps: List<Long>): CompletionTimeDistribution {
            if (timestamps.isEmpty()) return CompletionTimeDistribution(bucketCounts = emptyMap(), averageMinutesSinceMidnight = null)
            val zone = ZoneId.systemDefault()
            var totalMinutes = 0L
            val counts = mutableMapOf<TimeOfDayBucket, Int>()
            for (millis in timestamps) {
                val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
                totalMinutes += time.toSecondOfDay() / 60L
                val bucket = bucketFor(time)
                counts[bucket] = (counts[bucket] ?: 0) + 1
            }
            return CompletionTimeDistribution(
                bucketCounts = counts,
                averageMinutesSinceMidnight = (totalMinutes.toFloat() / timestamps.size),
            )
        }

        private fun bucketFor(time: LocalTime): TimeOfDayBucket = when {
            time.isBefore(LocalTime.of(5, 0)) -> TimeOfDayBucket.NIGHT
            time.isBefore(LocalTime.of(12, 0)) -> TimeOfDayBucket.MORNING
            time.isBefore(LocalTime.of(17, 0)) -> TimeOfDayBucket.AFTERNOON
            time.isBefore(LocalTime.of(21, 0)) -> TimeOfDayBucket.EVENING
            else -> TimeOfDayBucket.NIGHT
        }

        /** Pure so it's unit-testable without a DB -- see [getConsistencyStats]. Population (not sample) standard deviation, since [dailyFractions] is the whole window, not a sample of it. */
        fun consistencyStatsOf(dailyFractions: List<Float>): ConsistencyStats {
            if (dailyFractions.isEmpty()) return ConsistencyStats(meanFraction = 0f, standardDeviation = 0f, daysCounted = 0)
            val mean = dailyFractions.map { it.toDouble() }.average()
            val variance = dailyFractions.sumOf { val diff = it - mean; diff * diff } / dailyFractions.size
            return ConsistencyStats(
                meanFraction = mean.toFloat(),
                standardDeviation = sqrt(variance).toFloat(),
                daysCounted = dailyFractions.size,
            )
        }
    }
}
