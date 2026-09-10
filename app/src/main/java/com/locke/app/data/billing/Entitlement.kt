package com.locke.app.data.billing

import com.locke.app.domain.model.SubscriptionTier

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
