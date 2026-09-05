package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One instant [com.habitsfirst.androidclone.service.AppBlockAccessibilityService] actually
 * covered a blocked app or URL with the block screen -- i.e. the user tried to open
 * something locked. This is an impulse-control signal for self-review, not a block-list
 * config table (see [BlockedAppEntity]/[BlockedDomainEntity] for that): every row here is
 * one attempt, so the same target can (and usually does) appear many times.
 */
@Entity(
    tableName = "block_attempts",
    indices = [Index(value = ["date"])],
)
data class BlockAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** The blocked package name, or URL host, that triggered the block screen. */
    val target: String,
    /** ISO-8601 local date string, e.g. "2026-08-31" -- matches every other range-queried entity in this DB. */
    val date: String,
    val timestampEpochMillis: Long = System.currentTimeMillis(),
)
