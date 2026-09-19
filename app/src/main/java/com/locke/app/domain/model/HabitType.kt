package com.locke.app.domain.model

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
    SLEEP_HOURS,

    /**
     * Be physically near a saved location today (e.g. the gym) -- checked periodically
     * against the device's last-known location (see
     * [com.locke.app.service.LocationSyncWorker]), with a manual
     * "I was there" tap as a fallback.
     */
    VISIT_LOCATION,

    /** Contribute on GitHub today, for a saved username (see [com.locke.app.service.GithubSyncWorker]), with a manual fallback. */
    GITHUB_CONTRIBUTION,

    /** Code for a target number of minutes today, synced from the user's own WakaTime account (see [com.locke.app.service.WakaTimeSyncWorker]). */
    WAKATIME_CODING_MINUTES,

    /** Tap an NFC tag or scan a QR code -- whichever was set up for this habit -- to prove you're at a physical spot (see `ui/habit/ScanTagScreen`). */
    TAG_SCAN,
    ;

    /** Whether this habit type accumulates a numeric value toward [Habit.targetValue]. */
    val isMeasurable: Boolean
        get() = this == TIMED_MINUTES || this == APP_USAGE_MINUTES || this == STEPS ||
            this == WORKOUT_MINUTES || this == SLEEP_HOURS || this == WAKATIME_CODING_MINUTES

    /** Whether this habit type needs a target app selected. */
    val requiresTargetApp: Boolean
        get() = this == APP_USAGE_MINUTES

    /** Whether this habit type needs a target location saved (see [Habit.targetLatitude]/[Habit.targetLongitude]). */
    val requiresTargetLocation: Boolean
        get() = this == VISIT_LOCATION

    /** Whether this habit type needs a GitHub username saved. */
    val requiresGithubUsername: Boolean
        get() = this == GITHUB_CONTRIBUTION

    /** Whether this habit type needs an NFC/QR tag payload generated (see [Habit.tagPayload]). */
    val requiresTagSetup: Boolean
        get() = this == TAG_SCAN

    val unit: String
        get() = when (this) {
            STEPS -> "steps"
            TIMED_MINUTES, APP_USAGE_MINUTES, WORKOUT_MINUTES, WAKATIME_CODING_MINUTES -> "min"
            SLEEP_HOURS -> "hr"
            PHOTO, TALLY, VISIT_LOCATION, GITHUB_CONTRIBUTION, TAG_SCAN -> ""
        }

    /** Which [HabitTypeCategory] this type sorts into in the add-habit picker -- see that enum's KDoc. */
    val category: HabitTypeCategory
        get() = when (this) {
            TIMED_MINUTES, APP_USAGE_MINUTES, WORKOUT_MINUTES, SLEEP_HOURS, WAKATIME_CODING_MINUTES -> HabitTypeCategory.TIME
            TALLY, STEPS -> HabitTypeCategory.COUNT
            PHOTO, VISIT_LOCATION, GITHUB_CONTRIBUTION, TAG_SCAN -> HabitTypeCategory.PROOF
        }
}

/**
 * A higher-level grouping over [HabitType]'s eleven concrete types, so the add-habit flow
 * asks "what kind of goal is this?" first (three choices) rather than presenting all
 * eleven [HabitType]s flat -- see `AddEditHabitScreen`'s type picker.
 */
enum class HabitTypeCategory(val label: String, val hint: String) {
    TIME("Time", "Spend a target amount of time on it"),
    COUNT("Count", "A plain check-in or a number to hit, no timer"),
    PROOF("Proof", "Verified by a photo, a place, a tag, or a connected account"),
    ;

    /** Every [HabitType] in this category, in [HabitType]'s own declared order. */
    val types: List<HabitType> get() = HabitType.entries.filter { it.category == this }
}
