package com.locke.app.data.billing

import android.app.Activity
import com.locke.app.BuildConfig
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.domain.model.PremiumProduct
import com.locke.app.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [PlayBillingEntitlementRepository] with Settings -> Developer's "Developer
 * mode" toggle (`PreferencesRepository.isDeveloperModeEnabled`): while it's on, every
 * call site sees an always-active entitlement with no Play Billing purchase and no
 * backend round trip, so a debug build can be exercised end to end without paying.
 * Bound in place of [PlayBillingEntitlementRepository] in `di/BillingModule.kt` --
 * every other call site keeps going through the [EntitlementRepository] seam and needs
 * no changes.
 *
 * The `BuildConfig.DEBUG` check here is the actual gate, not just the Settings row's
 * visibility -- a release build ignores the stored preference entirely even if it were
 * somehow left on (e.g. carried over from a debug build sharing the same data), so this
 * can never bypass a real purchase in a build a user could install from Play.
 */
@Singleton
class DevModeEntitlementRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val delegate: PlayBillingEntitlementRepository,
) : EntitlementRepository {

    private val devModeActive: Flow<Boolean> =
        if (BuildConfig.DEBUG) preferencesRepository.isDeveloperModeEnabled else flowOf(false)

    override val entitlement: Flow<Entitlement> = combine(devModeActive, delegate.entitlement) { devMode, real ->
        if (devMode) DEV_ENTITLEMENT else real
    }

    override suspend fun isPremium(): Boolean = entitlement.first().isPremium

    override val products: Flow<List<PremiumProduct>> = delegate.products

    override suspend fun launchPurchase(activity: Activity, productId: String): Result<Unit> {
        // Already unlocked -- nothing to actually buy while developer mode is active.
        if (devModeActive.first()) return Result.success(Unit)
        return delegate.launchPurchase(activity, productId)
    }

    override suspend fun recordPurchase(tier: SubscriptionTier, expiresAtEpochMillis: Long?) =
        delegate.recordPurchase(tier, expiresAtEpochMillis)

    override suspend fun refresh() {
        // Nothing to reconcile against Play/the backend while developer mode fakes the result.
        if (!devModeActive.first()) delegate.refresh()
    }

    private companion object {
        val DEV_ENTITLEMENT = Entitlement(
            tier = SubscriptionTier.LIFETIME,
            isPremium = true,
            expiresAtEpochMillis = null,
        )
    }
}
