package com.habitsfirst.androidclone.util

/**
 * Frontend on/off switches for features whose data/sync/verification code stays fully
 * wired up either way -- flipping a flag back to `true` is the entire re-enable, no other
 * changes needed.
 */
object FeatureFlags {
    /**
     * Surfaces the GitHub/WakaTime habit types in the add/edit type picker and the
     * Settings "Connections" section. Off: [com.habitsfirst.androidclone.domain.model.HabitType.GITHUB_CONTRIBUTION]
     * and [com.habitsfirst.androidclone.domain.model.HabitType.WAKATIME_CODING_MINUTES]
     * keep their sync workers, DB columns, and Settings fields -- they're just not
     * offered to pick from.
     */
    const val GITHUB_WAKATIME_HABITS_ENABLED = false

    /**
     * Surfaces NFC read/write UI for [com.habitsfirst.androidclone.domain.model.HabitType.TAG_SCAN]
     * habits. Off: QR still works everywhere; only the "write to NFC tag" setup step and
     * the "tap your tag" confirm option disappear.
     */
    const val NFC_ENABLED = false
}
