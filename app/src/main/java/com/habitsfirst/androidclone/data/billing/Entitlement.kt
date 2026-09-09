package com.habitsfirst.androidclone.data.billing

import com.habitsfirst.androidclone.domain.model.SubscriptionTier

/**
 * The current state of a user's premium access.
 *
 * [isPremium] is the single source of truth callers should check -- never infer premium
 * status from `tier != SubscriptionTier.NONE` directly, since [isPremium] also accounts
 * for [expiresAtEpochMillis] having passed.
 */
data class Entitlement(
    val tier: SubscriptionTier,
    val isPremium: Boolean,
    val expiresAtEpochMillis: Long?,
)
