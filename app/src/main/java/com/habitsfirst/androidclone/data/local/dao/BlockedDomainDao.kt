package com.habitsfirst.androidclone.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitsfirst.androidclone.data.local.entity.BlockedDomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {

    @Query("SELECT domain FROM blocked_domains WHERE listId = :listId ORDER BY domain ASC")
    fun observeDomainsForList(listId: String): Flow<List<String>>

    /** Every custom-list domain, for [com.habitsfirst.androidclone.data.repository.UrlBlockRepository] to index by list in one query. */
    @Query("SELECT * FROM blocked_domains")
    fun observeAll(): Flow<List<BlockedDomainEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BlockedDomainEntity)

    @Query("DELETE FROM blocked_domains WHERE listId = :listId AND domain = :domain")
    suspend fun delete(listId: String, domain: String)
}
