package com.locke.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.locke.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

/** A completed todo's created/completed timestamps, for the time-to-complete stat. */
data class TodoCompletionTiming(val createdAtEpochMillis: Long, val completedAtEpochMillis: Long)

@Dao
interface TodoDao {

    /** Every todo due [today] or [tomorrow] -- see [Todo][com.locke.app.domain.model.Todo]. */
    @Query(
        """
        SELECT * FROM todos
        WHERE date = :today OR date = :tomorrow
        ORDER BY isDone ASC, date ASC, createdAtEpochMillis ASC
        """,
    )
    fun observeForDates(today: String, tomorrow: String): Flow<List<TodoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity): Long

    /** Still-pending todos due before [today] -- see [com.locke.app.data.repository.TodoRepository.getOverdueTodos]. */
    @Query("SELECT * FROM todos WHERE date < :today AND isDone = 0 ORDER BY date ASC, createdAtEpochMillis ASC")
    suspend fun getOverdue(today: String): List<TodoEntity>

    /** Bumps the given todos' due date forward -- see [com.locke.app.data.repository.TodoRepository.carryOverToToday]. */
    @Query("UPDATE todos SET date = :date WHERE id IN (:ids)")
    suspend fun setDates(ids: List<Long>, date: String)

    /** [completedAtEpochMillis] should be the current time when [isDone] is true, and null otherwise -- see [com.locke.app.data.repository.TodoRepository.setDone]. */
    @Query("UPDATE todos SET isDone = :isDone, completedAtEpochMillis = :completedAtEpochMillis WHERE id = :id")
    suspend fun setDone(id: Long, isDone: Boolean, completedAtEpochMillis: Long?)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("SELECT COUNT(*) FROM todos WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    /** Timing for every todo due in range that's actually been completed -- the data source for the average-minutes-to-complete stat. */
    @Query(
        """
        SELECT createdAtEpochMillis, completedAtEpochMillis FROM todos
        WHERE date BETWEEN :startDate AND :endDate AND isDone = 1 AND completedAtEpochMillis IS NOT NULL
        """,
    )
    suspend fun getCompletionTimingsInRange(startDate: String, endDate: String): List<TodoCompletionTiming>

    /** Every todo due within [startDate]..[endDate] inclusive -- for [com.locke.app.util.StatsExportUtil]'s data export, unlike [observeForDates]'s today/tomorrow-only window. */
    @Query(
        """
        SELECT * FROM todos
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC, createdAtEpochMillis ASC
        """,
    )
    suspend fun getForDateRange(startDate: String, endDate: String): List<TodoEntity>
}
