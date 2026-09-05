package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("UPDATE todos SET isDone = :isDone WHERE id = :id")
    suspend fun setDone(id: Long, isDone: Boolean)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("SELECT COUNT(*) FROM todos WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    /** Every todo due within [startDate]..[endDate] inclusive -- for [com.habitsfirst.androidclone.util.StatsExportUtil]'s data export, unlike [observeForDates]'s today/tomorrow-only window. */
    @Query(
        """
        SELECT * FROM todos
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC, createdAtEpochMillis ASC
        """,
    )
    suspend fun getForDateRange(startDate: String, endDate: String): List<TodoEntity>
}
