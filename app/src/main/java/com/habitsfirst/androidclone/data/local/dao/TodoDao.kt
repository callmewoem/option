package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

/** A completed todo's created/completed timestamps, for the time-to-complete stat. */
data class TodoCompletionTiming(val createdAtEpochMillis: Long, val completedAtEpochMillis: Long)

@Dao
interface TodoDao {

    /** Every todo due [today] or [tomorrow] -- see [Todo][com.habitsfirst.androidclone.domain.model.Todo]. */
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

    /** [completedAtEpochMillis] should be the current time when [isDone] is true, and null otherwise -- see [com.habitsfirst.androidclone.data.repository.TodoRepository.setDone]. */
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
}
