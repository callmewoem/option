package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A day the [com.habitsfirst.androidclone.data.repository.PenaltyRepository] has marked
 * as broken -- e.g. a MARK_STREAK_BROKEN penalty -- regardless of whether every gating
 * habit ended up completed that day. The streak calculation and heatmap both treat a
 * scarred date as a failed day.
 */
@Entity(tableName = "streak_scars")
data class StreakScarEntity(
    @PrimaryKey val date: String,
    val reason: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)
