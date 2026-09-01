package com.habitsfirst.androidclone.data.billing

import com.habitsfirst.androidclone.domain.model.SubscriptionTier

/**
 * The current state of a user's premium access.
 *
 * [isPremium] is the single source of truth callers should check -- never infer premium
 * status from `tier != SubscriptionTier.NONE`. Today [isPremium] is hardcoded `true`
 * regardless of [tier] (see [StubEntitlementRepository]), so the two can legitimately
 * disagree until real Play Billing is wired up.
 */
data class Entitlement(
    val tier: SubscriptionTier,
    val isPremium: Boolean,
    val expiresAtEpochMillis: Long?,
)
