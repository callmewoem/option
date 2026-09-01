package com.habitsfirst.androidclone.domain.model

import java.time.DayOfWeek

/**
 * A task shown on a single calendar day. Non-recurring by default -- [date] is the one
 * day it's due and [isDone] tracks it directly. When [repeatDays] is non-empty instead,
 * this is a recurring task (e.g. "hoover" every Sunday): it reappears on every date
 * whose [DayOfWeek] is in [repeatDays] from [date] (its start date) onward, and each
 * occurrence's done-state is tracked separately -- see [isDone]'s doc.
 */
data class Todo(
    val id: Long = 0L,
    val title: String,
    val date: String,
    /**
     * For a one-off task: whether it's done. For a recurring task, this is instead
     * *this occurrence's* done-state (e.g. "done for this Sunday") -- resolved by
     * whichever date the task was loaded for, not a single persistent flag.
     */
    val isDone: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    /** Days of the week this task recurs on. Empty means it's a one-off task for [date]. */
    val repeatDays: Set<DayOfWeek> = emptySet(),
) {
    val isRecurring: Boolean get() = repeatDays.isNotEmpty()
}
