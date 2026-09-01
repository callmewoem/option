package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import com.habitsfirst.androidclone.domain.model.BlockListSource
import com.habitsfirst.androidclone.domain.model.BlockMode
import com.habitsfirst.androidclone.domain.model.UrlBlockList

/**
 * Settings for one URL block list. The two premade lists ([BlockListSource.PREMADE_PORN]/
 * [BlockListSource.PREMADE_SOCIAL]) are seeded once with fixed ids by
 * [com.habitsfirst.androidclone.data.repository.UrlBlockRepository] and only ever have
 * their [isEnabled]/[blockMode] changed here -- their domains live in the bundled asset
 * files, not this table. A [BlockListSource.CUSTOM] row's domains live in
 * [BlockedDomainEntity].
 */
@Entity(tableName = "block_lists", primaryKeys = ["id"])
data class BlockListEntity(
    val id: String,
    val name: String,
    val source: BlockListSource,
    val blockMode: BlockMode,
    val isEnabled: Boolean,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

fun BlockListEntity.toDomain(domainCount: Int): UrlBlockList = UrlBlockList(
    id = id,
    name = name,
    source = source,
    blockMode = blockMode,
    isEnabled = isEnabled,
    domainCount = domainCount,
)
