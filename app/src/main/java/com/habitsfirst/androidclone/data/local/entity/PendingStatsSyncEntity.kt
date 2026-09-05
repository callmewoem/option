package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One daily summary that hasn't been successfully pushed to the accountability backend
 * yet -- [com.habitsfirst.androidclone.data.repository.AccountabilityRepository] queues
 * one of these whenever a push fails (no backend configured, unreachable, or a non-2xx
 * response), and retries the whole outbox opportunistically (app foreground, or the
 * next time sharing runs). A simple "try now, leave it queued on failure" -- no backoff
 * schedule or dedicated retry worker for this pass.
 *
 * [date] is the primary key: a later summary for the same day replaces the queued one
 * rather than piling up duplicates for a day that's since changed.
 */
@Entity(tableName = "pending_stats_sync")
data class PendingStatsSyncEntity(
    @PrimaryKey val date: String,
    val habitsCompleted: Int,
    val totalHabits: Int,
    val currentStreak: Int,
    val queuedAtEpochMillis: Long = System.currentTimeMillis(),
    val lastAttemptAtEpochMillis: Long? = null,
    val attemptCount: Int = 0,
)
