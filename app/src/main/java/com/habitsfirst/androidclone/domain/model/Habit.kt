package com.habitsfirst.androidclone.domain.model

/**
 * A habit the user tracks daily. Whether it gates their locked apps, is purely
 * tracked, or is an antihabit is controlled by [kind]; how its progress is measured
 * is controlled by [type].
 */
data class Habit(
    val id: Long = 0L,
    val name: String,
    val type: HabitType,
    val targetValue: Int,
    val targetPackageName: String? = null,
    val targetAppLabel: String? = null,
    val sortOrder: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val kind: HabitKind = HabitKind.GATING,
    /** If set, this habit auto-archives once the date has passed -- used for makeup habits. */
    val expiresAfterDate: String? = null,
) {
    val displayTarget: String
        get() = if (type == HabitType.CUSTOM) "" else "$targetValue ${type.unit}"
}

/**
 * A habit's progress for a single calendar day.
 *
 * For [HabitKind.ANTIHABIT] habits, [isCompleted] means "a slip was logged for this
 * day", not "done" -- callers rendering antihabit UI should invert the usual
 * complete/incomplete color language (see [HabitKind] docs).
 */
data class HabitProgress(
    val habit: Habit,
    val currentValue: Int,
    val isCompleted: Boolean,
) {
    val fraction: Float
        get() = if (habit.type == HabitType.CUSTOM) {
            if (isCompleted) 1f else 0f
        } else {
            (currentValue.toFloat() / habit.targetValue.toFloat()).coerceIn(0f, 1f)
        }
}
