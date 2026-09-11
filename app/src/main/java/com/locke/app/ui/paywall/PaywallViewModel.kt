package com.locke.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locke.app.data.billing.EntitlementRepository
import com.locke.app.data.repository.AnalyticsRepository
import com.locke.app.data.repository.ExperimentRepository
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.domain.model.PaywallPlanEmphasis
import com.locke.app.domain.model.PremiumFeature
import com.locke.app.domain.model.PremiumProduct
import com.locke.app.ui.navigation.Screen
import com.locke.app.util.AnalyticsEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaywallUiState(
    val feature: PremiumFeature = PremiumFeature.GENERAL,
    val isPremium: Boolean = false,
    val products: List<PremiumProduct> = emptyList(),
    val purchaseInFlight: Boolean = false,
    val errorMessage: String? = null,
    /** [com.locke.app.domain.model.ExperimentKeys.GATING_HABIT_CAP]'s current value -- see "Unlimited gating habits" bullet in [PaywallContent]. */
    val gatingHabitCap: Int = PreferencesRepository.MAX_FREE_GATING_HABITS,
    /** [com.locke.app.domain.model.ExperimentKeys.PAYWALL_PLAN_EMPHASIS] -- which plan [PaywallContent] sorts first and highlights as "Best value". */
    val planEmphasis: PaywallPlanEmphasis = PaywallPlanEmphasis.ANNUAL,
)

/** Drives the paywall -- see `ui/paywall/PaywallScreen.kt`. Shown during onboarding and from every premium-gated call site elsewhere in the app. */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlementRepository: EntitlementRepository,
    private val experimentRepository: ExperimentRepository,
    private val analyticsRepository: AnalyticsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val feature: PremiumFeature =
        savedStateHandle.get<String>(Screen.ARG_FEATURE)
            ?.let { runCatching { PremiumFeature.valueOf(it) }.getOrNull() }
            ?: PremiumFeature.GENERAL

    private val _purchaseInFlight = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    /** Whether entitlement was already premium the moment this screen opened -- so [onPremiumConfirmed] only logs a genuine new purchase, not just re-entering an already-premium paywall. Null until the first entitlement read arrives. */
    private var wasPremiumOnEntry: Boolean? = null
    private var hasLoggedPurchaseCompleted = false

    val uiState: StateFlow<PaywallUiState> = combine(
        entitlementRepository.entitlement,
        entitlementRepository.products,
        _purchaseInFlight,
        _errorMessage,
        combine(experimentRepository.gatingHabitCap, experimentRepository.paywallPlanEmphasis, ::Pair),
    ) { entitlement, products, purchaseInFlight, errorMessage, experiments ->
        PaywallUiState(
            feature = feature,
            isPremium = entitlement.isPremium,
            products = products,
            purchaseInFlight = purchaseInFlight,
            errorMessage = errorMessage,
            gatingHabitCap = experiments.first,
            planEmphasis = experiments.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState(feature = feature))

    init {
        // Products can take a moment to arrive from the store on first launch --
        // opportunistically re-check purchases too, in case one completed outside
        // this screen (a different session, a refund reversal, etc.).
        viewModelScope.launch { entitlementRepository.refresh() }
        viewModelScope.launch {
            wasPremiumOnEntry = entitlementRepository.entitlement.first().isPremium
            analyticsRepository.logEvent(
                AnalyticsEvents.PAYWALL_SHOWN,
                mapOf(
                    "feature" to feature.name,
                    "planEmphasis" to experimentRepository.paywallPlanEmphasis.first().name,
                    "gatingHabitCap" to experimentRepository.gatingHabitCap.first(),
                ),
            )
        }
    }

    fun onPurchaseClicked(activity: Activity, productId: String) {
        if (_purchaseInFlight.value) return
        _purchaseInFlight.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            analyticsRepository.logEvent(AnalyticsEvents.PAYWALL_CTA_TAPPED, mapOf("feature" to feature.name, "productId" to productId))
            entitlementRepository.launchPurchase(activity, productId)
                .onFailure { _errorMessage.value = it.message ?: "Couldn't start checkout -- try again." }
            _purchaseInFlight.value = false
        }
    }

    /** "Restore purchases" -- re-queries Play Billing and re-verifies against the backend, for a reinstall or a new device. */
    fun onRestoreClicked() {
        viewModelScope.launch { entitlementRepository.refresh() }
    }

    /**
     * Called from [PaywallScreen]/`OnboardingPaywallScreen` the moment [uiState]
     * reports premium -- logs a completed purchase exactly once, and only if this
     * device wasn't already premium when the screen opened (so simply reopening an
     * already-premium paywall never logs a phantom purchase).
     */
    fun onPremiumConfirmed() {
        if (hasLoggedPurchaseCompleted || wasPremiumOnEntry != false) return
        hasLoggedPurchaseCompleted = true
        viewModelScope.launch {
            analyticsRepository.logEvent(AnalyticsEvents.PURCHASE_COMPLETED, mapOf("feature" to feature.name))
        }
    }

    fun onErrorMessageShown() {
        _errorMessage.value = null
    }
}
