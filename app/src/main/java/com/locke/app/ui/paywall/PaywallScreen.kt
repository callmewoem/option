package com.locke.app.ui.paywall

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locke.app.R
import com.locke.app.domain.model.PremiumProduct
import com.locke.app.domain.model.SubscriptionTier
import com.locke.app.ui.components.LockeCard
import com.locke.app.ui.components.LockePrimaryButton
import com.locke.app.ui.components.LockeQuietButton
import com.locke.app.ui.theme.LockeColor

/**
 * The CTA every premium gate routes to (design spec §5: be specific about what's being
 * asked for and why) -- a photo-verification habit past the free check, the buddy
 * section in Settings, the free-tier habit cap, and onboarding's own pitch
 * (`ui/onboarding/OnboardingPaywallScreen.kt`, which reuses [PaywallContent] below with
 * its own skippable bottom bar instead of this screen's back arrow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    onPurchased: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isPremium) {
        if (state.isPremium) onPurchased()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locke Premium") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            PaywallContent(
                state = state,
                onPurchase = { productId -> viewModel.onPurchaseClicked(activity, productId) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            LockeQuietButton(text = "Restore purchases", onClick = viewModel::onRestoreClicked, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The pitch + product list shared by [PaywallScreen] and `OnboardingPaywallScreen`. */
@Composable
fun PaywallContent(state: PaywallUiState, onPurchase: (productId: String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = LockeColor.Brass)
        Spacer(modifier = Modifier.width(8.dp))
        Text(state.feature.headline, style = MaterialTheme.typography.headlineSmall)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(state.feature.pitch, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(20.dp))

    listOf(
        "Unlimited gating habits -- free plan covers 5.",
        "Unlimited AI photo checks -- free plan covers 3 a month.",
        "Unlimited accountability buddies -- free plan covers 1.",
    ).forEach { line ->
        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(modifier = Modifier.height(24.dp))

    if (state.products.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Loading prices…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        // Annual first -- the best-value option gets top billing, same principle as a
        // storefront's default-selected plan.
        state.products.sortedBy { it.tier != SubscriptionTier.ANNUAL }.forEach { product ->
            ProductCard(product = product, enabled = !state.purchaseInFlight, onPurchase = { onPurchase(product.productId) })
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProductCard(product: PremiumProduct, enabled: Boolean, onPurchase: () -> Unit) {
    val isBestValue = product.tier == SubscriptionTier.ANNUAL
    LockeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isBestValue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        borderColor = if (isBestValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.tier.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    product.formattedPrice + (product.billingPeriodLabel?.let { " $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isBestValue) {
                    Text("Best value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            LockePrimaryButton(text = if (product.tier == SubscriptionTier.LIFETIME) "Buy" else "Subscribe", onClick = onPurchase, enabled = enabled)
        }
    }
}
