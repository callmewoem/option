package com.locke.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.locke.app.data.local.entity.AnalyticsEventEntity

@Dao
interface AnalyticsEventDao {

    @Insert
    suspend fun insert(event: AnalyticsEventEntity)

    /** Oldest-first, capped at [limit] -- one upload batch's worth. See [AnalyticsUploadWorker][com.locke.app.service.AnalyticsUploadWorker]. */
    @Query("SELECT * FROM analytics_events ORDER BY id ASC LIMIT :limit")
    suspend fun getBatch(limit: Int): List<AnalyticsEventEntity>

    @Query("DELETE FROM analytics_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM analytics_events")
    suspend fun count(): Int
}
