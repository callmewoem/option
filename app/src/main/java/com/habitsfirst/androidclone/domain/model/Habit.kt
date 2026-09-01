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
    /** [IMAGE_VERIFICATION][HabitType.IMAGE_VERIFICATION]: what a proof photo must show. */
    val verificationPrompt: String? = null,
    /** [IMAGE_VERIFICATION][HabitType.IMAGE_VERIFICATION]: path to a saved example photo, if any. */
    val verificationExampleImagePath: String? = null,
) {
    val displayTarget: String
        get() = if (!type.isMeasurable) "" else "$targetValue ${type.unit}"
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
        get() = if (!habit.type.isMeasurable) {
            if (isCompleted) 1f else 0f
        } else {
            (currentValue.toFloat() / habit.targetValue.toFloat()).coerceIn(0f, 1f)
        }
}
