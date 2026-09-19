package com.locke.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.locke.app.data.local.entity.HabitListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitListDao {

    @Query("SELECT * FROM habit_lists ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<HabitListEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM habit_lists")
    suspend fun getMaxSortOrder(): Int

    @Insert
    suspend fun insert(list: HabitListEntity): Long

    @Delete
    suspend fun delete(list: HabitListEntity)
}
