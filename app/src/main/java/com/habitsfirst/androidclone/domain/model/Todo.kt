package com.habitsfirst.androidclone.domain.model

/**
 * A one-off task due either today or tomorrow -- todos aren't day-of-week dependent
 * (that's what a recurring [Habit] is for, via [Habit.scheduledDays]); a todo is just
 * a short-lived thing to get done, due on [date]. Left undone past its due date, a
 * todo doesn't just vanish or silently carry itself over: it's surfaced (see
 * [com.habitsfirst.androidclone.data.repository.TodoRepository.getOverdueTodos]) on
 * the app's next cold launch or resume on a later day, so the user can pick which of
 * yesterday's undone todos to keep for today (see [com.habitsfirst.androidclone.AppViewModel]).
 */
data class Todo(
    val id: Long = 0L,
    val title: String,
    val date: String,
    val isDone: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    /** When [isDone] was last set true -- null while pending, cleared back to null if un-done. Powers the time-to-complete stat. */
    val completedAtEpochMillis: Long? = null,
)
