package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.StreakScarEntity
import kotlinx.coroutines.flow.Flow

/** How often a given [StreakScarEntity.reason] shows up within a range, for a "why did streaks break" summary. */
data class ScarReasonCount(val reason: String, val count: Int)

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

    @Query(
        """
        SELECT reason, COUNT(*) as count FROM streak_scars
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY reason
        ORDER BY count DESC
        """,
    )
    suspend fun getReasonCountsInRange(startDate: String, endDate: String): List<ScarReasonCount>
}
