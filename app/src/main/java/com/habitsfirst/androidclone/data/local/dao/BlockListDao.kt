package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.BlockListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockListDao {

    @Query("SELECT * FROM block_lists ORDER BY createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<BlockListEntity>>

    @Query("SELECT * FROM block_lists WHERE id = :id")
    suspend fun getById(id: String): BlockListEntity?

    /** Used to seed the two premade lists once -- a no-op if the row already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(list: BlockListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: BlockListEntity)

    @Query("DELETE FROM block_lists WHERE id = :id")
    suspend fun deleteById(id: String)
}
