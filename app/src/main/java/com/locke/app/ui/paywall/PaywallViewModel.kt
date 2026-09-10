package com.locke.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locke.app.data.billing.EntitlementRepository
import com.locke.app.domain.model.PremiumFeature
import com.locke.app.domain.model.PremiumProduct
import com.locke.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaywallUiState(
    val feature: PremiumFeature = PremiumFeature.GENERAL,
    val isPremium: Boolean = false,
    val products: List<PremiumProduct> = emptyList(),
    val purchaseInFlight: Boolean = false,
    val errorMessage: String? = null,
)

/** Drives the paywall -- see `ui/paywall/PaywallScreen.kt`. Shown during onboarding and from every premium-gated call site elsewhere in the app. */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlementRepository: EntitlementRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val feature: PremiumFeature =
        savedStateHandle.get<String>(Screen.ARG_FEATURE)
            ?.let { runCatching { PremiumFeature.valueOf(it) }.getOrNull() }
            ?: PremiumFeature.GENERAL

    private val _purchaseInFlight = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PaywallUiState> = combine(
        entitlementRepository.entitlement,
        entitlementRepository.products,
        _purchaseInFlight,
        _errorMessage,
    ) { entitlement, products, purchaseInFlight, errorMessage ->
        PaywallUiState(
            feature = feature,
            isPremium = entitlement.isPremium,
            products = products,
            purchaseInFlight = purchaseInFlight,
            errorMessage = errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState(feature = feature))

    init {
        // Products can take a moment to arrive from the store on first launch --
        // opportunistically re-check purchases too, in case one completed outside
        // this screen (a different session, a refund reversal, etc.).
        viewModelScope.launch { entitlementRepository.refresh() }
    }

    fun onPurchaseClicked(activity: Activity, productId: String) {
        if (_purchaseInFlight.value) return
        _purchaseInFlight.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            entitlementRepository.launchPurchase(activity, productId)
                .onFailure { _errorMessage.value = it.message ?: "Couldn't start checkout -- try again." }
            _purchaseInFlight.value = false
        }
    }

    /** "Restore purchases" -- re-queries Play Billing and re-verifies against the backend, for a reinstall or a new device. */
    fun onRestoreClicked() {
        viewModelScope.launch { entitlementRepository.refresh() }
    }

    fun onErrorMessageShown() {
        _errorMessage.value = null
    }
}
