package com.habitsfirst.androidclone.domain.model

/**
 * A habit the user must complete each day to keep their locked apps unlocked.
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
) {
    val displayTarget: String
        get() = if (type == HabitType.CUSTOM) "" else "$targetValue ${type.unit}"
}

/**
 * A habit's progress for a single calendar day.
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
