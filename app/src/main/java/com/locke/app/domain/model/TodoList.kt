package com.locke.app.domain.model

/**
 * A user-defined bucket to partition [Todo]s into (e.g. "Work", "Errands") -- purely
 * organizational, unlike [Habit.kind]/[Habit.type]: it carries no behavior of its own,
 * just a name and a manual position. A [Todo] with `listId == null` isn't in any list
 * ("Inbox"), which is why deleting a [TodoList] un-assigns its todos rather than
 * deleting them -- see the `ON DELETE SET NULL` foreign key on
 * [com.locke.app.data.local.entity.TodoEntity.listId].
 */
data class TodoList(
    val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)
