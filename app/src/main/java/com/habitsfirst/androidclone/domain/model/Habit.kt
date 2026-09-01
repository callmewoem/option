package com.habitsfirst.androidclone.domain.model

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * A habit the user tracks. Whether it gates their locked apps, is purely tracked, or
 * is an antihabit is controlled by [kind]; how its progress is measured is controlled
 * by [type]; which days it's due on is controlled by [scheduledDays].
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
    val kind: HabitKind = HabitKind.GATING,
    /** If set, this habit auto-archives once the date has passed -- used for makeup habits. */
    val expiresAfterDate: String? = null,
    /**
     * Position (0 = easiest) in an onboarding "ease-in" ramp, null if this habit isn't
     * part of one. Habits with a non-null order were chosen together at onboarding and
     * ranked by difficulty; only the lowest-order one starts GATING, the rest start
     * TRACKED and are promoted one at a time by [com.habitsfirst.androidclone.data.repository.EaseInRepository].
     */
    val easeInOrder: Int? = null,
    /**
     * Days of the week this habit is due on. Empty (the default) means every day --
     * only a non-empty set narrows it to specific days, e.g. "hoover" every Sunday.
     * A GATING habit only counts toward that day's lock/streak on a day it's due.
     */
    val scheduledDays: Set<DayOfWeek> = emptySet(),
) {
    val displayTarget: String
        get() = if (!type.isMeasurable) "" else "$targetValue ${type.unit}"

    val isDaily: Boolean get() = scheduledDays.isEmpty()

    /** e.g. "Every day", "Every Sun", or "Every Mon, Wed, Fri". */
    val scheduleLabel: String get() = scheduledDays.toScheduleLabel()

    fun isDueOn(dayOfWeek: DayOfWeek): Boolean = isDaily || dayOfWeek in scheduledDays
}

/** e.g. "Every day", "Every Sun", or "Every Mon, Wed, Fri" -- empty means every day. */
fun Set<DayOfWeek>.toScheduleLabel(): String {
    if (isEmpty()) return "Every day"
    val ordered = DayOfWeek.values().filter { it in this }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    return "Every $ordered"
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
        get() = if (!habit.type.isMeasurable) {
            if (isCompleted) 1f else 0f
        } else {
            (currentValue.toFloat() / habit.targetValue.toFloat()).coerceIn(0f, 1f)
        }
}
