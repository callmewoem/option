package com.habitsfirst.androidclone.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.PremiumProduct
import com.habitsfirst.androidclone.domain.model.SubscriptionTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The real [EntitlementRepository]: connects to Play Billing, lists
 * [SubscriptionProducts]' live prices, launches the purchase flow, and -- on every
 * purchase update -- verifies the result against Locke's own backend
 * ([BackendPurchaseVerifier], which checks it against the real Play Developer API)
 * before trusting it, rather than taking the on-device `Purchase` object's word for it.
 * [entitlement] itself always reads from [PreferencesRepository.subscriptionState], the
 * last value the backend confirmed -- so it keeps working (read-only) with no Play
 * Billing connection or no connectivity at all, same "cache what the backend last said"
 * shape as [com.habitsfirst.androidclone.data.repository.AccountabilityRepository].
 */
@Singleton
class PlayBillingEntitlementRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesRepository: PreferencesRepository,
    private val backendPurchaseVerifier: BackendPurchaseVerifier,
) : EntitlementRepository, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())

    init {
        scope.launch {
            runCatching {
                ensureConnected()
                queryProducts()
                reconcilePurchases()
            }
        }
    }

    override val entitlement: Flow<Entitlement> = preferencesRepository.subscriptionState.map { stored ->
        val notExpired = stored.expiresAtEpochMillis == null || stored.expiresAtEpochMillis > System.currentTimeMillis()
        Entitlement(
            tier = stored.tier,
            isPremium = stored.tier != SubscriptionTier.NONE && notExpired,
            expiresAtEpochMillis = stored.expiresAtEpochMillis,
        )
    }

    override suspend fun isPremium(): Boolean = entitlement.first().isPremium

    override val products: Flow<List<PremiumProduct>> = _productDetails.map { list -> list.mapNotNull { it.toPremiumProduct() } }

    override suspend fun recordPurchase(tier: SubscriptionTier, expiresAtEpochMillis: Long?) {
        preferencesRepository.setSubscriptionState(tier, expiresAtEpochMillis)
    }

    override suspend fun launchPurchase(activity: Activity, productId: String): Result<Unit> = runCatching {
        ensureConnected()
        var details = _productDetails.value.firstOrNull { it.productId == productId }
        if (details == null) {
            queryProducts()
            details = _productDetails.value.firstOrNull { it.productId == productId }
        }
        checkNotNull(details) { "That product isn't available right now." }

        val productDetailsParamsList = if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            checkNotNull(offerToken) { "No offer is currently available for this subscription." }
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build(),
            )
        } else {
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
        }

        val flowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(productDetailsParamsList).build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        check(result.responseCode == BillingClient.BillingResponseCode.OK) {
            result.debugMessage.ifBlank { "Couldn't start checkout (${result.responseCode})." }
        }
    }

    /** Play Billing's async purchase callback -- fires after [launchPurchase]'s flow completes, and again for any purchase made outside this session (another device, a refund reversal, etc.) once reconciled by [refresh]. */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        scope.launch { purchases.forEach { handlePurchase(it) } }
    }

    override suspend fun refresh() {
        runCatching {
            ensureConnected()
            reconcilePurchases()
            backendPurchaseVerifier.fetchEntitlement()?.let {
                preferencesRepository.setSubscriptionState(it.tier, it.expiresAtEpochMillis)
            }
        }
    }

    private suspend fun reconcilePurchases() {
        val subs = billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        val inApp = billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        (subs.purchasesList + inApp.purchasesList).forEach { handlePurchase(it) }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull() ?: return

        backendPurchaseVerifier.verifyPurchase(productId, purchase.purchaseToken)?.let {
            preferencesRepository.setSubscriptionState(it.tier, it.expiresAtEpochMillis)
        }

        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            runCatching { billingClient.acknowledgePurchase(params) }
        }
    }

    private suspend fun queryProducts() {
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SubscriptionProducts.MONTHLY_SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SubscriptionProducts.ANNUAL_SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SubscriptionProducts.LIFETIME_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        val subsResult = billingClient.queryProductDetails(subsParams)
        val inAppResult = billingClient.queryProductDetails(inAppParams)
        _productDetails.value = subsResult.productDetailsList.orEmpty() + inAppResult.productDetailsList.orEmpty()
    }

    private suspend fun ensureConnected() {
        if (billingClient.isReady) return
        suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (continuation.isActive) continuation.resume(Unit)
                        } else if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Billing unavailable: ${billingResult.debugMessage}"),
                            )
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        // billingClient.isReady will be false on the next call, which
                        // re-triggers ensureConnected() -- no explicit retry needed here.
                    }
                },
            )
        }
    }

    private fun ProductDetails.toPremiumProduct(): PremiumProduct? {
        val tier = when (productId) {
            SubscriptionProducts.MONTHLY_SUBSCRIPTION_ID -> SubscriptionTier.MONTHLY
            SubscriptionProducts.ANNUAL_SUBSCRIPTION_ID -> SubscriptionTier.ANNUAL
            SubscriptionProducts.LIFETIME_PRODUCT_ID -> SubscriptionTier.LIFETIME
            else -> return null
        }
        return if (productType == BillingClient.ProductType.SUBS) {
            val phase = subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull() ?: return null
            val periodLabel = when (tier) {
                SubscriptionTier.ANNUAL -> "per year"
                else -> "per month"
            }
            PremiumProduct(productId, tier, phase.formattedPrice, periodLabel)
        } else {
            val price = oneTimePurchaseOfferDetails?.formattedPrice ?: return null
            PremiumProduct(productId, tier, price, billingPeriodLabel = null)
        }
    }
}
