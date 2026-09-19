package com.locke.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.locke.app.data.local.entity.TodoListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoListDao {

    @Query("SELECT * FROM todo_lists ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<TodoListEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM todo_lists")
    suspend fun getMaxSortOrder(): Int

    @Insert
    suspend fun insert(list: TodoListEntity): Long

    @Update
    suspend fun update(list: TodoListEntity)

    @Delete
    suspend fun delete(list: TodoListEntity)
}
