package com.habitsfirst.androidclone.data.billing

import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder [EntitlementRepository] used until real Play Billing is wired up. The
 * stored tier/expiry are persisted for real through [PreferencesRepository] -- so a
 * future [recordPurchase] call from the eventual purchase flow has somewhere real to
 * land -- but the premium *read* is hardcoded true below, independent of that stored
 * state.
 */
@Singleton
class StubEntitlementRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : EntitlementRepository {

    // TODO(billing): this always reports the user as premium. Once a real
    // PlayBillingEntitlementRepository is wired up and bound in place of this
    // stub (see di/BillingModule.kt), remove the hardcoded `true` below and
    // derive `isPremium` from the stored tier/expiry instead.
    override val entitlement: Flow<Entitlement> = preferencesRepository.subscriptionState.map { stored ->
        Entitlement(
            tier = stored.tier,
            isPremium = true,
            expiresAtEpochMillis = stored.expiresAtEpochMillis,
        )
    }

    // TODO(billing): same hardcoding as `entitlement` above -- always true regardless
    // of the stored tier. Remove once real billing is wired up.
    override suspend fun isPremium(): Boolean = true

    override suspend fun recordPurchase(tier: SubscriptionTier, expiresAtEpochMillis: Long?) {
        preferencesRepository.setSubscriptionState(tier, expiresAtEpochMillis)
    }

    override suspend fun refresh() {
        // No-op placeholder. Once real billing is wired up, this is where a
        // "re-query Play Billing for the user's current purchases" call will go.
    }
}
