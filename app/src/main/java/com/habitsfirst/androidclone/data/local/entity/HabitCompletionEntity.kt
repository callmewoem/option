package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A habit's progress for one calendar day, keyed by [habitId] + [date].
 * [date] is stored as an ISO-8601 local date string, e.g. "2026-08-31".
 */
@Entity(
    tableName = "habit_completions",
    indices = [Index(value = ["habitId", "date"], unique = true)],
)
data class HabitCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val habitId: Long,
    val date: String,
    val currentValue: Int = 0,
    val isCompleted: Boolean = false,
    val completedAtEpochMillis: Long? = null,
)
