package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habitsfirst.androidclone.domain.model.AccountabilityBuddy
import com.habitsfirst.androidclone.domain.model.BuddyConnectionStatus
import com.habitsfirst.androidclone.domain.model.DailySummary

/**
 * Cached copy of one paired buddy's latest known state, so the buddy list in Settings
 * still renders with no connectivity. Written by
 * [com.habitsfirst.androidclone.data.repository.AccountabilityRepository] whenever a
 * backend call for buddies succeeds -- untouched otherwise, so a failed refresh just
 * leaves the last-known state on screen instead of blanking it out.
 */
@Entity(tableName = "accountability_buddies")
data class AccountabilityBuddyEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val pairingCode: String,
    /** One of "PENDING" / "CONNECTED" / "ERROR" -- see [BuddyConnectionStatus]. [statusMessage] carries the ERROR case's detail. */
    val status: String,
    val statusMessage: String? = null,
    val lastSummaryDate: String? = null,
    val lastSummaryHabitsCompleted: Int? = null,
    val lastSummaryTotalHabits: Int? = null,
    val lastSummaryCurrentStreak: Int? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

fun AccountabilityBuddyEntity.toDomain(): AccountabilityBuddy = AccountabilityBuddy(
    id = id,
    displayName = displayName,
    pairingCode = pairingCode,
    status = when (status) {
        "CONNECTED" -> BuddyConnectionStatus.Connected
        "ERROR" -> BuddyConnectionStatus.Error(statusMessage ?: "Sync failed.")
        else -> BuddyConnectionStatus.Pending
    },
    lastSummary = lastSummaryDate?.let { date ->
        DailySummary(
            date = date,
            habitsCompleted = lastSummaryHabitsCompleted ?: 0,
            totalHabits = lastSummaryTotalHabits ?: 0,
            currentStreak = lastSummaryCurrentStreak ?: 0,
        )
    },
)

fun AccountabilityBuddy.toEntity(): AccountabilityBuddyEntity = AccountabilityBuddyEntity(
    id = id,
    displayName = displayName,
    pairingCode = pairingCode,
    status = when (status) {
        is BuddyConnectionStatus.Connected -> "CONNECTED"
        is BuddyConnectionStatus.Error -> "ERROR"
        is BuddyConnectionStatus.Pending -> "PENDING"
    },
    statusMessage = (status as? BuddyConnectionStatus.Error)?.message,
    lastSummaryDate = lastSummary?.date,
    lastSummaryHabitsCompleted = lastSummary?.habitsCompleted,
    lastSummaryTotalHabits = lastSummary?.totalHabits,
    lastSummaryCurrentStreak = lastSummary?.currentStreak,
)
