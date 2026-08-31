package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val targetValue: Int,
    val targetPackageName: String?,
    val targetAppLabel: String?,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    /** Soft-delete flag so historical [HabitCompletionEntity] rows stay meaningful. */
    val isArchived: Boolean = false,
    val kind: String = HabitKind.GATING.name,
    val expiresAfterDate: String? = null,
)

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    type = HabitType.valueOf(type),
    targetValue = targetValue,
    targetPackageName = targetPackageName,
    targetAppLabel = targetAppLabel,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
    kind = runCatching { HabitKind.valueOf(kind) }.getOrDefault(HabitKind.GATING),
    expiresAfterDate = expiresAfterDate,
)

fun Habit.toEntity(isArchived: Boolean = false): HabitEntity = HabitEntity(
    id = id,
    name = name,
    type = type.name,
    targetValue = targetValue,
    targetPackageName = targetPackageName,
    targetAppLabel = targetAppLabel,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
    isArchived = isArchived,
    kind = kind.name,
    expiresAfterDate = expiresAfterDate,
)
