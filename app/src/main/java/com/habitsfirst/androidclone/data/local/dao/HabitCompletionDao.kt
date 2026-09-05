package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity
import kotlinx.coroutines.flow.Flow

/** One day's aggregate gating-habit completion, for the heatmap. */
data class DayCompletionCounts(
    val date: String,
    val completedCount: Int,
    val totalCount: Int,
)

/** One habit's completed-entry count within a date range, for the stats distribution. */
data class HabitCompletedCount(
    val habitId: Long,
    val completedCount: Int,
)

@Dao
interface HabitCompletionDao {

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    fun observeCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getCompletion(habitId: Long, date: String): HabitCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: HabitCompletionEntity)

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    suspend fun getCompletionsForDateOnce(date: String): List<HabitCompletionEntity>

    /** Dates on which [habitId] has a completed entry -- "green days" on its heatmap strip. */
    @Query(
        """
        SELECT date FROM habit_completions
        WHERE habitId = :habitId AND date BETWEEN :startDate AND :endDate AND isCompleted = 1
        """,
    )
    suspend fun getCompletedDatesForHabit(habitId: Long, startDate: String, endDate: String): List<String>

    /** Per-day completed/total counts across every GATING habit, for the aggregate heatmap. */
    @Query(
        """
        SELECT c.date as date,
               SUM(CASE WHEN c.isCompleted = 1 THEN 1 ELSE 0 END) as completedCount,
               COUNT(*) as totalCount
        FROM habit_completions c
        INNER JOIN habits h ON h.id = c.habitId
        WHERE h.kind = 'GATING' AND c.date BETWEEN :startDate AND :endDate
        GROUP BY c.date
        """,
    )
    suspend fun getDayCompletionCountsInRange(startDate: String, endDate: String): List<DayCompletionCounts>

    /** Completed-entry counts per habit within a range, for the per-habit stats distribution. */
    @Query(
        """
        SELECT habitId, SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completedCount
        FROM habit_completions
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY habitId
        """,
    )
    suspend fun getCompletedCountsByHabitInRange(startDate: String, endDate: String): List<HabitCompletedCount>

    /** Every completion timestamp in range, for the time-of-day-of-completion distribution ("always finishing at the last minute?"). */
    @Query(
        """
        SELECT completedAtEpochMillis FROM habit_completions
        WHERE date BETWEEN :startDate AND :endDate AND isCompleted = 1 AND completedAtEpochMillis IS NOT NULL
        """,
    )
    suspend fun getCompletionTimestampsInRange(startDate: String, endDate: String): List<Long>
}
