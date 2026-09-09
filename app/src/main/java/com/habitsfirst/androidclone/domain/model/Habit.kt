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
    /** [PHOTO][HabitType.PHOTO] only: what a proof photo must show. */
    val verificationPrompt: String? = null,
    /** [PHOTO][HabitType.PHOTO] only: path to a saved example photo, if any. */
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
    /** [HabitType.VISIT_LOCATION] only: the saved target's latitude. */
    val targetLatitude: Double? = null,
    /** [HabitType.VISIT_LOCATION] only: the saved target's longitude. */
    val targetLongitude: Double? = null,
    /** [HabitType.VISIT_LOCATION] only: how close counts as "there", in meters. */
    val targetRadiusMeters: Int? = null,
    /** [HabitType.VISIT_LOCATION] only: a friendly name for the saved target, e.g. "The gym". */
    val targetLocationLabel: String? = null,
    /** [HabitType.GITHUB_CONTRIBUTION] only: the GitHub username to check for today's activity. */
    val targetGithubUsername: String? = null,
    /**
     * [HabitType.TAG_SCAN] only: the random payload written to this habit's NFC tag and/or
     * encoded in its QR code -- generated once when the type is first picked (see
     * `AddEditHabitViewModel.onTypeChanged`) and compared against whatever's scanned in
     * `ui/habit/ScanTagScreen`.
     */
    val tagPayload: String? = null,
) {
    val displayTarget: String
        get() = if (!type.isMeasurable) "" else "$targetValue ${type.unit}"

    val isDaily: Boolean get() = scheduledDays.isEmpty()

    /** [HabitType.VISIT_LOCATION] only: whether a target location has actually been saved yet. */
    val hasTargetLocation: Boolean get() = targetLatitude != null && targetLongitude != null

    /** e.g. "Every day", "Every Sun", or "Every Mon, Wed, Fri". */
    val scheduleLabel: String get() = scheduledDays.toScheduleLabel()

    fun isDueOn(dayOfWeek: DayOfWeek): Boolean = isDaily || dayOfWeek in scheduledDays

    companion object {
        /** Default/fallback radius for a [HabitType.VISIT_LOCATION] habit's target, in meters -- shared by the add/edit form's radius picker and [com.habitsfirst.androidclone.service.LocationSyncWorker]'s fallback for an old row saved before a radius was chosen. */
        const val DEFAULT_VISIT_LOCATION_RADIUS_METERS = 150
    }
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
