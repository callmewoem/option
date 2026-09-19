package com.locke.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.locke.app.domain.model.TodoList

/** A user-defined todo partition -- see [TodoList]. */
@Entity(tableName = "todo_lists")
data class TodoListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

fun TodoListEntity.toDomain(): TodoList = TodoList(
    id = id,
    name = name,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
)
