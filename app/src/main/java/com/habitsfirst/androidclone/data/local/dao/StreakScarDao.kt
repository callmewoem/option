package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.StreakScarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakScarDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scar: StreakScarEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM streak_scars WHERE date = :date)")
    suspend fun isScarred(date: String): Boolean

    @Query("SELECT date FROM streak_scars WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getScarredDatesInRange(startDate: String, endDate: String): List<String>

    @Query("SELECT date FROM streak_scars WHERE date BETWEEN :startDate AND :endDate")
    fun observeScarredDatesInRange(startDate: String, endDate: String): Flow<List<String>>

    /** Full scar rows (date + why) in range -- [getScarredDatesInRange] only has the dates, which is all the heatmap/streak calc need, but [com.habitsfirst.androidclone.util.StatsExportUtil]'s export wants the reason too. */
    @Query("SELECT * FROM streak_scars WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getScarsInRange(startDate: String, endDate: String): List<StreakScarEntity>
}
