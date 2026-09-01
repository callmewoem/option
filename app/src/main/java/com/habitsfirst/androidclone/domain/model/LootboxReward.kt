package com.habitsfirst.androidclone.domain.model

/** What a daily lootbox can contain. See [com.habitsfirst.androidclone.data.repository.LootboxRepository]. */
sealed class LootboxReward {
    /** A token redeemable for one minute of unblocked apps, any time except bedtime. */
    data object GracePeriod : LootboxReward()

    /** A token that force-completes one of today's gating habits without doing it. */
    data object TaskSkip : LootboxReward()

    /** Permanently unlocks a new accent [ThemeVariant] for the app's theme. */
    data class ThemeUnlock(val variant: ThemeVariant) : LootboxReward()

    /** A purely cosmetic gold-star flourish on today's heatmap cell. */
    data object GoldStar : LootboxReward()
}
