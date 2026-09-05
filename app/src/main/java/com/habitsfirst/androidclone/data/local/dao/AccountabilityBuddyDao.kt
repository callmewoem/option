package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.AccountabilityBuddyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountabilityBuddyDao {

    @Query("SELECT * FROM accountability_buddies ORDER BY displayName ASC")
    fun observeAll(): Flow<List<AccountabilityBuddyEntity>>

    @Query("SELECT * FROM accountability_buddies ORDER BY displayName ASC")
    suspend fun getAllOnce(): List<AccountabilityBuddyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(buddy: AccountabilityBuddyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(buddies: List<AccountabilityBuddyEntity>)

    @Query("DELETE FROM accountability_buddies WHERE id = :id")
    suspend fun delete(id: String)
}
