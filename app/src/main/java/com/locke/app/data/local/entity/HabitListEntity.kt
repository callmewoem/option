package com.locke.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.locke.app.domain.model.HabitList

/** A user-defined habit partition -- see [HabitList]. */
@Entity(tableName = "habit_lists")
data class HabitListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

fun HabitListEntity.toDomain(): HabitList = HabitList(
    id = id,
    name = name,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
)
