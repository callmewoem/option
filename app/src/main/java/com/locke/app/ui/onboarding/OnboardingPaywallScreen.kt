package com.locke.app.ui.onboarding

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locke.app.ui.components.LockeGhostButton
import com.locke.app.ui.paywall.PaywallContent
import com.locke.app.ui.paywall.PaywallViewModel

/**
 * Onboarding's final step: the Premium pitch, right after every free feature is already
 * set up -- the CTA lands once the person has seen what the free app does, not before.
 * Always skippable ("Maybe later"): the free tier is a complete, usable app on its own
 * (design spec §5 -- never a dead end), this is upsell, not a gate. Either path (skip,
 * or a purchase completing) finishes onboarding the same way, via [onboardingViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingPaywallScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onboardingViewModel: OnboardingViewModel,
    paywallViewModel: PaywallViewModel = hiltViewModel(),
) {
    val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    val paywallState by paywallViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(onboardingState.finished) {
        if (onboardingState.finished) onFinish()
    }
    // A completed purchase finishes onboarding the same as "Maybe later" -- there's
    // nothing else this step is waiting on once entitlement is confirmed.
    LaunchedEffect(paywallState.isPremium) {
        if (paywallState.isPremium) {
            paywallViewModel.onPremiumConfirmed()
            onboardingViewModel.finishOnboarding()
        }
    }
    LaunchedEffect(paywallState.errorMessage) {
        paywallState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { OnboardingTopBar(step = 6, totalSteps = 6, onBack = onBack) },
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                LockeGhostButton(
                    text = "Maybe later",
                    onClick = onboardingViewModel::finishOnboarding,
                    enabled = !onboardingState.isFinishing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
            OnboardingKicker("Part six -- go further")
            PaywallContent(
                state = paywallState,
                onPurchase = { productId -> paywallViewModel.onPurchaseClicked(activity, productId) },
            )
        }
    }
}
