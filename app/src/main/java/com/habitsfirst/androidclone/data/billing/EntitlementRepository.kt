package com.habitsfirst.androidclone.data.billing

import com.habitsfirst.androidclone.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow

/**
 * The seam a future real Play-Billing-backed implementation will sit behind. Every
 * caller that needs to know "is this user entitled to premium?" should go through here
 * -- never query Play Billing (or anything else) directly -- so that swapping the stub
 * bound in `di/BillingModule.kt` for a real `PlayBillingEntitlementRepository` requires
 * zero changes at the call sites.
 */
interface EntitlementRepository {
    /** The user's current entitlement state. See [Entitlement.isPremium] for the field to actually check. */
    val entitlement: Flow<Entitlement>

    /** Convenience one-shot read of [entitlement]'s `isPremium` flag. */
    suspend fun isPremium(): Boolean

    /**
     * Persists a purchase result. Not called by anything yet -- this is here for a
     * future purchase flow to call once it lands, so the tier survives process death
     * ahead of a real billing integration re-confirming it.
     */
    suspend fun recordPurchase(tier: SubscriptionTier, expiresAtEpochMillis: Long?)

    /**
     * No-op placeholder today. Once real billing is wired up, this is where a
     * "re-query Play Billing for the user's current purchases" call will go.
     */
    suspend fun refresh()
}
