package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType
import java.time.DayOfWeek

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
    /** [HabitType.CUSTOM] only: require a proof photo before a check-in counts as done. */
    val requiresPhotoVerification: Boolean = false,
    /** Set only when [requiresPhotoVerification]: what a proof photo must show. */
    val verificationPrompt: String? = null,
    /** Set only when [requiresPhotoVerification]: path to a saved example photo, if any. */
    val verificationExampleImagePath: String? = null,
    val kind: String = HabitKind.GATING.name,
    val expiresAfterDate: String? = null,
    val easeInOrder: Int? = null,
    /** Bitmask over [DayOfWeek.value] (bit `value - 1`), see [toDayOfWeekSet]. 0 means every day. */
    val scheduledDaysMask: Int = 0,
)

/** Shared by [HabitEntity.scheduledDaysMask] -- kept here since it's the only entity that still needs it. */
fun Set<DayOfWeek>.toDaysMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun Int.toDayOfWeekSet(): Set<DayOfWeek> =
    DayOfWeek.values().filterTo(mutableSetOf()) { (this shr (it.value - 1)) and 1 == 1 }

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    type = HabitType.valueOf(type),
    targetValue = targetValue,
    targetPackageName = targetPackageName,
    targetAppLabel = targetAppLabel,
    sortOrder = sortOrder,
    createdAtEpochMillis = createdAtEpochMillis,
    requiresPhotoVerification = requiresPhotoVerification,
    verificationPrompt = verificationPrompt,
    verificationExampleImagePath = verificationExampleImagePath,
    kind = runCatching { HabitKind.valueOf(kind) }.getOrDefault(HabitKind.GATING),
    expiresAfterDate = expiresAfterDate,
    easeInOrder = easeInOrder,
    scheduledDays = scheduledDaysMask.toDayOfWeekSet(),
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
    requiresPhotoVerification = requiresPhotoVerification,
    verificationPrompt = verificationPrompt,
    verificationExampleImagePath = verificationExampleImagePath,
    kind = kind.name,
    expiresAfterDate = expiresAfterDate,
    easeInOrder = easeInOrder,
    scheduledDaysMask = scheduledDays.toDaysMask(),
)
