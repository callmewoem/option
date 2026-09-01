package com.habitsfirst.androidclone.domain.model

/**
 * What a [Habit] does for the day, independent of its [HabitType] (which just
 * describes how progress is measured).
 */
enum class HabitKind(val label: String) {
    /** Must be completed today or the apps on your block list stay locked. */
    GATING("Gating"),

    /** Logged and shown on the heatmap, but never blocks anything -- a nice-to-have. */
    TRACKED("Tracked"),

    /**
     * Silence-is-success: a day with no logged entry is a clean day (shown green on the
     * heatmap). Logging an entry records a slip for that day (shown red) and can trigger
     * a penalty -- see [com.habitsfirst.androidclone.data.repository.PenaltyRepository].
     */
    ANTIHABIT("Antihabit"),
}
