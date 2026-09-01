package com.habitsfirst.androidclone.domain.model

/**
 * The kinds of habits Habits First can gate your apps behind.
 *
 * Each type defines how progress is measured and, in turn, how the app
 * figures out whether the habit is "done" for the day.
 */
enum class HabitType {
    /** Walk a target number of steps today (Health Connect, with manual fallback). */
    STEPS,

    /** Spend a target number of minutes exercising (manual timer or Health Connect). */
    EXERCISE_MINUTES,

    /** Spend a target number of minutes meditating using the built-in timer. */
    MEDITATION_MINUTES,

    /** Actually use a specific app (e.g. Duolingo) for a target number of minutes. */
    APP_USAGE_MINUTES,

    /**
     * A manual check-in with no automatic tracking -- optionally gated on submitting a
     * proof photo (see [Habit.requiresPhotoVerification]) that's checked against a
     * description and/or an example photo by a vision model before it counts as done.
     */
    CUSTOM;

    /** Whether this habit type accumulates a numeric value toward [Habit.targetValue]. */
    val isMeasurable: Boolean
        get() = this != CUSTOM

    /** Whether this habit type needs a target app selected. */
    val requiresTargetApp: Boolean
        get() = this == APP_USAGE_MINUTES

    val unit: String
        get() = when (this) {
            STEPS -> "steps"
            EXERCISE_MINUTES, MEDITATION_MINUTES, APP_USAGE_MINUTES -> "min"
            CUSTOM -> ""
        }
}
