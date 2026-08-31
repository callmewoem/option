package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.HabitCompletionDao
import com.habitsfirst.androidclone.data.local.dao.HabitDao
import com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity
import com.habitsfirst.androidclone.data.local.entity.toDomain
import com.habitsfirst.androidclone.data.local.entity.toEntity
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao,
) {
    fun observeHabits(): Flow<List<Habit>> =
        habitDao.observeActiveHabits().map { list -> list.map { it.toDomain() } }

    fun observeHabitCount(): Flow<Int> = habitDao.observeActiveHabitCount()

    /** Today's habits paired with today's progress, in display order. */
    fun observeTodayProgress(): Flow<List<HabitProgress>> {
        val today = DateProvider.todayString()
        return combine(
            habitDao.observeActiveHabits(),
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

    /** Emits true once every active habit has a completed entry for today. */
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

    /** Active habits that track time spent in a specific app, for the usage-tracking worker. */
    suspend fun getAppUsageHabitsOnce(): List<Habit> =
        habitDao.getActiveHabitsOnce()
            .map { it.toDomain() }
            .filter { it.type == HabitType.APP_USAGE_MINUTES && it.targetPackageName != null }

    suspend fun getProgressOnce(habitId: Long, date: String = DateProvider.todayString()): Int =
        completionDao.getCompletion(habitId, date)?.currentValue ?: 0

    /** Current unbroken streak of days where every active habit was completed, ending today or yesterday. */
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
        val activeHabitCount = habitDao.getIncompleteHabitCountForDate(date)
        val completions = completionDao.getCompletionsForDateOnce(date)
        // A day counts only if there was at least one habit tracked and none incomplete.
        return completions.isNotEmpty() && activeHabitCount == 0
    }
}
