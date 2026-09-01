package com.habitsfirst.androidclone.domain.model

/**
 * The kinds of habits Habits First can gate your apps behind.
 *
 * Each type defines how progress is measured and, in turn, how the app
 * figures out whether the habit is "done" for the day. Order here is display
 * order in the type picker (see `AddEditHabitScreen`).
 */
enum class HabitType {
    /** Spend a target number of minutes on something, tracked with the built-in timer (workouts, meditation, anything else timed). */
    TIMED_MINUTES,

    /** Actually use a specific app (e.g. Duolingo) for a target number of minutes. */
    APP_USAGE_MINUTES,

    /**
     * A manual check-in gated on submitting a proof photo that's checked against a
     * description and/or an example photo by a vision model before it counts as done.
     */
    PHOTO,

    /** A plain manual check-in, no automatic tracking or verification -- just tap it done. */
    TALLY,

    /** Walk a target number of steps today (Health Connect, with manual fallback). */
    STEPS,

    /** Work out for a target number of minutes today (Health Connect, with manual fallback). */
    WORKOUT_MINUTES,

    /** Sleep a target number of hours (Health Connect, trailing 24h window, with manual fallback). */
    SLEEP_HOURS;

    /** Whether this habit type accumulates a numeric value toward [Habit.targetValue]. */
    val isMeasurable: Boolean
        get() = this == TIMED_MINUTES || this == APP_USAGE_MINUTES || this == STEPS ||
            this == WORKOUT_MINUTES || this == SLEEP_HOURS

    /** Whether this habit type needs a target app selected. */
    val requiresTargetApp: Boolean
        get() = this == APP_USAGE_MINUTES

    val unit: String
        get() = when (this) {
            STEPS -> "steps"
            TIMED_MINUTES, APP_USAGE_MINUTES, WORKOUT_MINUTES -> "min"
            SLEEP_HOURS -> "hr"
            PHOTO, TALLY -> ""
        }
}
