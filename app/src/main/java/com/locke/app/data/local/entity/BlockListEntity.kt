package com.locke.app.data.local.entity

import androidx.room.Entity
import com.locke.app.domain.model.BlockListSource
import com.locke.app.domain.model.BlockMode
import com.locke.app.domain.model.UrlBlockList

/**
 * Settings for one URL block list. The two premade lists ([BlockListSource.PREMADE_PORN]/
 * [BlockListSource.PREMADE_SOCIAL]) are seeded once with fixed ids by
 * [com.locke.app.data.repository.UrlBlockRepository] and only ever have
 * their [isEnabled]/[blockMode] changed here -- their domains live in
 * [com.locke.app.util.PremadeBlocklists] (a bundled seed, kept current by
 * periodic fetches from its upstream source), not this table. A [BlockListSource.CUSTOM]
 * row's domains live in [BlockedDomainEntity].
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
