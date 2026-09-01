package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.habitsfirst.androidclone.domain.model.Todo
import java.time.DayOfWeek

/**
 * A task, one-off or recurring. [date] is the single due date for a one-off task, or
 * the start date for a recurring one (see [repeatDaysMask]); [isDone] only applies to
 * a one-off task -- a recurring task's per-occurrence done-state lives in
 * [TodoCompletionEntity] instead, keyed by date.
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val date: String,
    val isDone: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    /** Bitmask over [DayOfWeek.value] (bit `value - 1`). 0 means non-recurring. */
    val repeatDaysMask: Int = 0,
)

/**
 * One recurring task's done-state for a single occurrence, keyed by [todoId] + [date].
 * Only written for tasks whose [TodoEntity.repeatDaysMask] is non-zero.
 */
@Entity(
    tableName = "todo_completions",
    indices = [Index(value = ["todoId", "date"], unique = true)],
)
data class TodoCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val todoId: Long,
    val date: String,
    val isDone: Boolean = false,
)

fun Set<DayOfWeek>.toRepeatDaysMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun Int.toDayOfWeekSet(): Set<DayOfWeek> =
    DayOfWeek.values().filterTo(mutableSetOf()) { (this shr (it.value - 1)) and 1 == 1 }

fun Todo.toEntity(): TodoEntity = TodoEntity(
    id = id,
    title = title,
    date = date,
    isDone = isDone,
    createdAtEpochMillis = createdAtEpochMillis,
    repeatDaysMask = repeatDays.toRepeatDaysMask(),
)
