package com.locke.app.domain.model

/**
 * A user-defined bucket to partition [Habit]s into (e.g. "Morning routine", "Health") --
 * purely organizational, unlike [Habit.kind]/[Habit.type]: it carries no behavior of its
 * own, just a name and a manual position. A [Habit] with `listId == null` isn't in any
 * list, which is why deleting a [HabitList] un-assigns its habits rather than deleting
 * them -- see the `ON DELETE SET NULL` foreign key on
 * [com.locke.app.data.local.entity.HabitEntity.listId].
 */
data class HabitList(
    val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)
