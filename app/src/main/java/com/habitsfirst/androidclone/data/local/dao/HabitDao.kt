package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habitsfirst.androidclone.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAtEpochMillis ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isArchived = 0 AND kind = :kind ORDER BY sortOrder ASC, createdAtEpochMillis ASC")
    fun observeActiveHabitsByKind(kind: String): Flow<List<HabitEntity>>

    /**
     * Active [kind] habits due on the day [dayBit] (`1 shl (dayOfWeek.value - 1)`)
     * represents -- a habit whose [HabitEntity.scheduledDaysMask] is 0 is due every
     * day; see [HabitDao] callers for how [dayBit] is derived from a date.
     */
    @Query(
        """
        SELECT * FROM habits WHERE isArchived = 0 AND kind = :kind
        AND (scheduledDaysMask = 0 OR (scheduledDaysMask & :dayBit) != 0)
        ORDER BY sortOrder ASC, createdAtEpochMillis ASC
        """,
    )
    fun observeActiveHabitsByKindForDate(kind: String, dayBit: Int): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAtEpochMillis ASC")
    suspend fun getActiveHabitsOnce(): List<HabitEntity>

    @Query("SELECT COUNT(*) FROM habits WHERE isArchived = 0")
    fun observeActiveHabitCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET isArchived = 1 WHERE id = :habitId")
    suspend fun archive(habitId: Long)

    /** Cleans up expired makeup habits (see [com.habitsfirst.androidclone.data.repository.PenaltyRepository]). */
    @Query("UPDATE habits SET isArchived = 1 WHERE isArchived = 0 AND expiresAfterDate IS NOT NULL AND expiresAfterDate < :date")
    suspend fun archiveExpiredHabits(date: String)

    @Query("SELECT MAX(sortOrder) FROM habits")
    suspend fun getMaxSortOrder(): Int?

    /** Whether the user has any active GATING habit at all, regardless of which days it's due -- see [com.habitsfirst.androidclone.data.repository.HabitRepository.isDateFullyComplete]. */
    @Query("SELECT COUNT(*) FROM habits WHERE isArchived = 0 AND kind = 'GATING'")
    suspend fun getActiveGatingHabitCount(): Int

    /**
     * Number of active GATING habits due on [dayBit]'s day that do NOT yet have a
     * completed [com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity]
     * row for [date]. Zero means every gating habit due today is done -- the signal the
     * app-blocker, streak calculation, and Home's "remaining habits" all key off of.
     * TRACKED and ANTIHABIT habits never gate unlocking, so they're excluded here, and
     * a habit not due on [dayBit]'s day (see [observeActiveHabitsByKindForDate]) never
     * counts as incomplete on it.
     */
    @Query(
        """
        SELECT COUNT(*) FROM habits h
        WHERE h.isArchived = 0 AND h.kind = 'GATING'
        AND (h.scheduledDaysMask = 0 OR (h.scheduledDaysMask & :dayBit) != 0)
        AND NOT EXISTS (
            SELECT 1 FROM habit_completions c
            WHERE c.habitId = h.id AND c.date = :date AND c.isCompleted = 1
        )
        """,
    )
    fun observeIncompleteHabitCountForDate(date: String, dayBit: Int): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM habits h
        WHERE h.isArchived = 0 AND h.kind = 'GATING'
        AND (h.scheduledDaysMask = 0 OR (h.scheduledDaysMask & :dayBit) != 0)
        AND NOT EXISTS (
            SELECT 1 FROM habit_completions c
            WHERE c.habitId = h.id AND c.date = :date AND c.isCompleted = 1
        )
        """,
    )
    suspend fun getIncompleteHabitCountForDate(date: String, dayBit: Int): Int
}
