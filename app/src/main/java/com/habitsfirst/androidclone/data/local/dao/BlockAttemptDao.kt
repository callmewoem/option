package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.BlockAttemptEntity
import kotlinx.coroutines.flow.Flow

/** One day's blocked-open-attempt count, for a distribution chart. */
data class DailyAttemptCount(val date: String, val count: Int)

@Dao
interface BlockAttemptDao {

    @Insert
    suspend fun insert(attempt: BlockAttemptEntity)

    @Query("SELECT COUNT(*) FROM block_attempts WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    /** Live version of [getCountForDate], for a Home chip that should update the moment a new attempt is logged rather than waiting for an unrelated recompute. */
    @Query("SELECT COUNT(*) FROM block_attempts WHERE date = :date")
    fun observeCountForDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_attempts WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getCountInRange(startDate: String, endDate: String): Int

    /** Per-day attempt counts within a range, for the stats screen's distribution chart. */
    @Query(
        """
        SELECT date, COUNT(*) as count FROM block_attempts
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        """,
    )
    suspend fun getDailyCountsInRange(startDate: String, endDate: String): List<DailyAttemptCount>
}
