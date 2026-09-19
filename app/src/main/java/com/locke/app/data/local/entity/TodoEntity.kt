package com.locke.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.locke.app.domain.model.Todo

/** A one-off task, due either today or tomorrow -- see [Todo]. */
@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = TodoListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("listId")],
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val date: String,
    val isDone: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    /** When [isDone] was last set true -- null while pending, cleared back to null if un-done. */
    val completedAtEpochMillis: Long? = null,
    /** Which [TodoListEntity] this todo is partitioned into -- null means unpartitioned ("Inbox"). */
    val listId: Long? = null,
)

fun TodoEntity.toDomain(): Todo = Todo(
    id = id,
    title = title,
    date = date,
    isDone = isDone,
    createdAtEpochMillis = createdAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    listId = listId,
)

fun Todo.toEntity(): TodoEntity = TodoEntity(
    id = id,
    title = title,
    date = date,
    isDone = isDone,
    createdAtEpochMillis = createdAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    listId = listId,
)
