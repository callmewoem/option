package com.habitsfirst.androidclone.domain.model

/**
 * A one-off task due either today or tomorrow -- todos aren't day-of-week dependent
 * (that's what a recurring [Habit] is for, via [Habit.scheduledDays]); a todo is just
 * a short-lived thing to get done, due on [date].
 */
data class Todo(
    val id: Long = 0L,
    val title: String,
    val date: String,
    val isDone: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)
