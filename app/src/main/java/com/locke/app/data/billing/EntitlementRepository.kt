package com.locke.app.data.billing

import android.app.Activity
import com.locke.app.domain.model.PremiumProduct
import com.locke.app.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow

/**
 * The seam every premium-gated call site goes through -- never query Play Billing (or
 * anything else) directly, so that swapping [PlayBillingEntitlementRepository] (bound in
 * `di/BillingModule.kt`) for a different implementation requires zero changes at the call
 * sites. [StubEntitlementRepository] is the simplest correct implementation (local-only,
 * unverified) -- useful as a reference and in tests.
 */
interface EntitlementRepository {
    /** The user's current entitlement state. See [Entitlement.isPremium] for the field to actually check. */
    val entitlement: Flow<Entitlement>

    /** Convenience one-shot read of [entitlement]'s `isPremium` flag. */
    suspend fun isPremium(): Boolean

    /** Every purchasable premium product, with live store-formatted pricing. Empty until the store has responded at least once. */
    val products: Flow<List<PremiumProduct>>

    /**
     * Launches Play Billing's purchase flow for [productId] from [activity] (Play Billing
     * requires a live `Activity` to show its checkout sheet over). Returns once the flow
     * has *launched* successfully, not once it's *completed* -- the actual result arrives
     * asynchronously and is reflected in [entitlement] once the purchase is verified and
     * recorded (see [PlayBillingEntitlementRepository]'s purchase-update listener).
     */
    suspend fun launchPurchase(activity: Activity, productId: String): Result<Unit>

    /**
     * Persists a purchase result locally. Called by [PlayBillingEntitlementRepository]
     * once a purchase is verified against Locke's backend -- not meant to be called
     * directly from UI.
     */
    suspend fun recordPurchase(tier: SubscriptionTier, expiresAtEpochMillis: Long?)

    /** Re-queries Play Billing for this device's current purchases and re-verifies them against the backend. */
    suspend fun refresh()
}
