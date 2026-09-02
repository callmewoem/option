package com.habitsfirst.androidclone.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.util.PermissionUtils

@Composable
fun OnboardingPermissionsScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Cheap way to re-read permission state when the user comes back from Settings.
    var refreshTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { refreshTick++ }

    val hasUsageAccess = remember(refreshTick) { PermissionUtils.hasUsageAccess(context) }
    val hasAccessibility = remember(refreshTick) { PermissionUtils.isAccessibilityServiceEnabled(context) }
    val hasOverlay = remember(refreshTick) { PermissionUtils.hasOverlayPermission(context) }

    Scaffold(
        topBar = { OnboardingTopBar(step = 3, totalSteps = 3, onBack = onBack) },
        bottomBar = {
            Button(
                onClick = {
                    viewModel.finishOnboarding()
                },
                enabled = !state.isFinishing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(stringResource(R.string.onboarding_finish))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            OnboardingKicker(stringResource(R.string.onboarding_kicker_permissions))
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_permissions_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            PermissionCard(
                title = stringResource(R.string.permission_usage_access_title),
                description = stringResource(R.string.permission_usage_access_desc),
                granted = hasUsageAccess,
                onGrant = { context.startActivity(PermissionUtils.usageAccessSettingsIntent(context)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionCard(
                title = stringResource(R.string.permission_accessibility_title),
                description = stringResource(R.string.permission_accessibility_desc),
                granted = hasAccessibility,
                onGrant = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionCard(
                title = stringResource(R.string.permission_overlay_title),
                description = stringResource(R.string.permission_overlay_desc),
                granted = hasOverlay,
                onGrant = { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) },
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Onboarding only sets up the basics (a handful of apps, a couple of
            // starter habits) -- this is a deliberate pointer at the rest of what
            // Locke can do, so it isn't left undiscovered in Settings.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.onboarding_permissions_whats_next_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.onboarding_permissions_whats_next_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (state.finished) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onFinish() }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (granted) {
                    Text(
                        stringResource(R.string.permission_granted),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                } else {
                    OutlinedButton(onClick = onGrant) {
                        Text(stringResource(R.string.permission_grant))
                    }
                }
            }
        }
    }
}
