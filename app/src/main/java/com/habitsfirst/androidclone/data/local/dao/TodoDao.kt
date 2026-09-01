package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

/**
 * One task occurrence as read for a given date: a plain (non-`@Entity`) projection
 * joining `todos` with `todo_completions`, not a stored row.
 */
data class TodoRow(
    val id: Long,
    val title: String,
    val date: String,
    val createdAtEpochMillis: Long,
    val repeatDaysMask: Int,
    val isDone: Boolean,
)

@Dao
interface TodoDao {

    /**
     * Every task due on [date]: one-off tasks dated exactly [date], plus recurring
     * tasks (started on or before [date]) whose [dayBit] -- `1 shl (dayOfWeek.value - 1)`
     * for [date]'s day of week -- is set in their repeat mask. A recurring task's
     * `isDone` is resolved from [com.habitsfirst.androidclone.data.local.entity.TodoCompletionEntity]
     * for this exact date, defaulting to false if that occurrence hasn't been touched yet.
     */
    @Query(
        """
        SELECT t.id AS id, t.title AS title, t.date AS date,
               t.createdAtEpochMillis AS createdAtEpochMillis, t.repeatDaysMask AS repeatDaysMask,
               CASE WHEN t.repeatDaysMask = 0 THEN t.isDone ELSE COALESCE(c.isDone, 0) END AS isDone
        FROM todos t
        LEFT JOIN todo_completions c ON c.todoId = t.id AND c.date = :date
        WHERE (t.repeatDaysMask = 0 AND t.date = :date)
           OR (t.repeatDaysMask != 0 AND t.date <= :date AND (t.repeatDaysMask & :dayBit) != 0)
        ORDER BY isDone ASC, t.createdAtEpochMillis ASC
        """,
    )
    fun observeForDate(date: String, dayBit: Int): Flow<List<TodoRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity): Long

    /** One-off tasks only -- see [setRecurringOccurrenceDone] for recurring ones. */
    @Query("UPDATE todos SET isDone = :isDone WHERE id = :id")
    suspend fun setDone(id: Long, isDone: Boolean)

    @Query(
        """
        INSERT INTO todo_completions (todoId, date, isDone) VALUES (:todoId, :date, :isDone)
        ON CONFLICT(todoId, date) DO UPDATE SET isDone = :isDone
        """,
    )
    suspend fun setRecurringOccurrenceDone(todoId: Long, date: String, isDone: Boolean)

    @Delete
    suspend fun delete(todo: TodoEntity)

    /** Called alongside [delete] when removing a whole recurring series -- see [com.habitsfirst.androidclone.data.repository.TodoRepository.delete]. */
    @Query("DELETE FROM todo_completions WHERE todoId = :id")
    suspend fun deleteOccurrencesFor(id: Long)

    @Query("SELECT COUNT(*) FROM todos WHERE date = :date")
    suspend fun getCountForDate(date: String): Int
}
