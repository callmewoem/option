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
    /** [HabitType.IMAGE_VERIFICATION]: what a proof photo must show. */
    val verificationPrompt: String? = null,
    /** [HabitType.IMAGE_VERIFICATION]: path to a saved example photo, if any. */
    val verificationExampleImagePath: String? = null,
    val kind: String = HabitKind.GATING.name,
    val expiresAfterDate: String? = null,
    val easeInOrder: Int? = null,
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
    verificationPrompt = verificationPrompt,
    verificationExampleImagePath = verificationExampleImagePath,
    kind = runCatching { HabitKind.valueOf(kind) }.getOrDefault(HabitKind.GATING),
    expiresAfterDate = expiresAfterDate,
    easeInOrder = easeInOrder,
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
    verificationPrompt = verificationPrompt,
    verificationExampleImagePath = verificationExampleImagePath,
    kind = kind.name,
    expiresAfterDate = expiresAfterDate,
    easeInOrder = easeInOrder,
)
