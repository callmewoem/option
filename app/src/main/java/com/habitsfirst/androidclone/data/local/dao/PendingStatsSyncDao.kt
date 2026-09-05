package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.PendingStatsSyncEntity

@Dao
interface PendingStatsSyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pending: PendingStatsSyncEntity)

    @Query("SELECT * FROM pending_stats_sync ORDER BY date ASC")
    suspend fun getAllOnce(): List<PendingStatsSyncEntity>

    @Query("DELETE FROM pending_stats_sync WHERE date = :date")
    suspend fun delete(date: String)

    @Query("SELECT COUNT(*) FROM pending_stats_sync")
    suspend fun count(): Int
}
