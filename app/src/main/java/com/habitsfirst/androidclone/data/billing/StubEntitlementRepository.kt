package com.habitsfirst.androidclone.data.billing

import android.app.Activity
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.PremiumProduct
import com.habitsfirst.androidclone.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The simplest correct [EntitlementRepository]: derives `isPremium` purely from whatever
 * tier/expiry is locally stored, with no purchase flow and no server verification of its
 * own. Not bound anywhere in the app (see `di/BillingModule.kt`, which binds
 * [PlayBillingEntitlementRepository] instead) -- kept as a reference implementation and
 * for tests that need an [EntitlementRepository] without a real Play Billing connection.
 */
@Singleton
class StubEntitlementRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : EntitlementRepository {

    override val entitlement: Flow<Entitlement> = preferencesRepository.subscriptionState.map { stored ->
        val notExpired = stored.expiresAtEpochMillis == null || stored.expiresAtEpochMillis > System.currentTimeMillis()
        Entitlement(
            tier = stored.tier,
            isPremium = stored.tier != SubscriptionTier.NONE && notExpired,
            expiresAtEpochMillis = stored.expiresAtEpochMillis,
        )
    }

    override suspend fun isPremium(): Boolean = entitlement.first().isPremium

    override val products: Flow<List<PremiumProduct>> = flowOf(emptyList())

    override suspend fun launchPurchase(activity: Activity, productId: String): Result<Unit> =
        Result.failure(IllegalStateException("Billing isn't available in this build."))

    override suspend fun recordPurchase(tier: SubscriptionTier, expiresAtEpochMillis: Long?) {
        preferencesRepository.setSubscriptionState(tier, expiresAtEpochMillis)
    }

    override suspend fun refresh() {
        // No real store to re-query -- entitlement is already exactly what's stored.
    }
}
