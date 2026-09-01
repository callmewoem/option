package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habitsfirst.androidclone.domain.model.Todo

/** A one-off task, due either today or tomorrow -- see [Todo]. */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val date: String,
    val isDone: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

fun TodoEntity.toDomain(): Todo = Todo(
    id = id,
    title = title,
    date = date,
    isDone = isDone,
    createdAtEpochMillis = createdAtEpochMillis,
)

fun Todo.toEntity(): TodoEntity = TodoEntity(
    id = id,
    title = title,
    date = date,
    isDone = isDone,
    createdAtEpochMillis = createdAtEpochMillis,
)
