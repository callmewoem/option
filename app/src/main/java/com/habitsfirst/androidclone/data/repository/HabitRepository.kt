package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.HabitCompletionDao
import com.habitsfirst.androidclone.data.local.dao.HabitDao
import com.habitsfirst.androidclone.data.local.dao.StreakScarDao
import com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity
import com.habitsfirst.androidclone.data.local.entity.toDomain
import com.habitsfirst.androidclone.data.local.entity.toEntity
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A habit's completion rate over a stats window. For an ANTIHABIT, [rate] is the
 * *clean* rate (days without a logged slip) -- the inverse of the raw completed-entry
 * count, since a completion row there means a slip, not a done day.
 */
data class HabitCompletionStat(val habit: Habit, val rate: Float)

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

    fun observeTodayProgressByKind(kind: HabitKind): Flow<List<HabitProgress>> {
        val today = DateProvider.todayString()
        return combine(
            habitDao.observeActiveHabitsByKind(kind.name),
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
    fun observeAllHabitsCompletedToday(): Flow<Boolean> =
        habitDao.observeIncompleteHabitCountForDate(DateProvider.todayString()).map { it == 0 }

    /** One-shot check used by the accessibility service before it locks the screen. */
    suspend fun areAllHabitsCompletedForDate(date: String = DateProvider.todayString()): Boolean =
        habitDao.getIncompleteHabitCountForDate(date) == 0

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

    suspend fun setCustomHabitDone(habitId: Long, done: Boolean, date: String = DateProvider.todayString()) {
        setProgress(habitId, if (done) 1 else 0, target = 1, date = date)
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
            val totalDays = if (effectiveStart > rangeEnd) 0 else ChronoUnit.DAYS.between(effectiveStart, rangeEnd) + 1
            val completed = countsByHabit[habit.id]?.completedCount ?: 0
            val rawRate = if (totalDays <= 0) 0f else (completed.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
            val rate = if (habit.kind == HabitKind.ANTIHABIT) 1f - rawRate else rawRate
            HabitCompletionStat(habit, rate)
        }
    }

    /**
     * Dates in range where [habitId] has a completed entry -- for GATING/TRACKED habits
     * that's a "green day"; for ANTIHABIT habits it's a "slip day" and callers should
     * invert the color mapping.
     */
    suspend fun getCompletedDatesForHabit(habitId: Long, startDate: String, endDate: String): Set<String> =
        completionDao.getCompletedDatesForHabit(habitId, startDate, endDate).toSet()

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

    private suspend fun isDateFullyComplete(date: String): Boolean {
        if (streakScarDao.isScarred(date)) return false
        val activeHabitCount = habitDao.getIncompleteHabitCountForDate(date)
        // Only count real GATING activity -- a TRACKED/ANTIHABIT-only day shouldn't
        // read as a "complete" gating day just because some other habit was logged.
        val gatingCounts = completionDao.getDayCompletionCountsInRange(date, date).firstOrNull()
        return (gatingCounts?.totalCount ?: 0) > 0 && activeHabitCount == 0
    }
}
