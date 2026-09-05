package com.habitsfirst.androidclone.domain.model

/**
 * A snapshot of one day's habit progress, shared between buddies. Used both as the
 * outbound payload this device pushes for itself and as the cached shape of a buddy's
 * last-known state -- deliberately small (just what a buddy card needs to show), and
 * built only from data [com.habitsfirst.androidclone.data.repository.HabitRepository]
 * already tracks today, independent of any other stats work in flight elsewhere.
 */
data class DailySummary(
    val date: String,
    val habitsCompleted: Int,
    val totalHabits: Int,
    val currentStreak: Int,
)

/** Where a paired buddy's connection currently stands, from this device's point of view. */
sealed class BuddyConnectionStatus {
    /** Pairing code exchanged with the backend, but no summary has been fetched from them yet. */
    object Pending : BuddyConnectionStatus()

    /** Their last-known summary was fetched successfully at least once. */
    object Connected : BuddyConnectionStatus()

    /** The most recent attempt to reach the backend for this buddy failed -- [message] is shown in the buddy list row. */
    data class Error(val message: String) : BuddyConnectionStatus()
}

/**
 * One accountability buddy paired with this user. Cached locally (see
 * `data/local/entity/AccountabilityBuddyEntity.kt`) so the buddy list still renders with
 * no connectivity -- [status]/[lastSummary] reflect whatever
 * [com.habitsfirst.androidclone.data.repository.AccountabilityRepository] last managed
 * to fetch, not necessarily this instant's true state.
 */
data class AccountabilityBuddy(
    val id: String,
    val displayName: String,
    /** The pairing code that was redeemed to add this buddy, kept around for display/debugging. */
    val pairingCode: String,
    val status: BuddyConnectionStatus,
    val lastSummary: DailySummary?,
)

/** A freshly minted code this device can share so someone else can add it as a buddy. */
data class PairingCode(val code: String)
