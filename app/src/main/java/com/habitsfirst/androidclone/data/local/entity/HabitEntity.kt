package com.habitsfirst.androidclone.data.local.entity

import androidx.room.ColumnInfo
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
    /** [HabitType.PHOTO] only: what a proof photo must show. */
    val verificationPrompt: String? = null,
    /** [HabitType.PHOTO] only: path to a saved example photo, if any. */
    val verificationExampleImagePath: String? = null,
    /**
     * Stored as [HabitKind.name]. `@ColumnInfo(defaultValue)` here isn't just documentation --
     * MIGRATION_2_3 (see `data/local/migrations`) adds this column with
     * `DEFAULT 'GATING'` for existing rows, and Room's schema validator compares that
     * literal default against this annotation on every app start; letting them drift
     * would make Room reject the migrated database as a schema mismatch.
     */
    @ColumnInfo(defaultValue = "'GATING'")
    val kind: String = HabitKind.GATING.name,
    val expiresAfterDate: String? = null,
    val easeInOrder: Int? = null,
    /**
     * Bitmask over [DayOfWeek.value] (bit `value - 1`), see [toDayOfWeekSet]. 0 means
     * every day. `@ColumnInfo(defaultValue)` must track MIGRATION_5_6's
     * `DEFAULT 0` -- see the note on [kind].
     */
    @ColumnInfo(defaultValue = "0")
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
    verificationPrompt = verificationPrompt,
    verificationExampleImagePath = verificationExampleImagePath,
    kind = kind.name,
    expiresAfterDate = expiresAfterDate,
    easeInOrder = easeInOrder,
    scheduledDaysMask = scheduledDays.toDaysMask(),
)
