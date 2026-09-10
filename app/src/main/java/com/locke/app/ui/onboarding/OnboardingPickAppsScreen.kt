package com.locke.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locke.app.R
import com.locke.app.domain.model.UrlBlockList
import com.locke.app.ui.components.LockePrimaryButton

@Composable
fun OnboardingPickAppsScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { OnboardingTopBar(step = 2, totalSteps = 6, onBack = onBack) },
        bottomBar = {
            LockePrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                enabled = state.canContinueFromApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                OnboardingKicker(stringResource(R.string.onboarding_kicker_apps))
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.onboarding_pick_apps_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_pick_apps_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (state.selectedPackageNames.isEmpty()) {
                        stringResource(R.string.onboarding_pick_apps_none_selected)
                    } else if (state.selectedPackageNames.size == 1) {
                        stringResource(R.string.onboarding_pick_apps_selected_count, 1)
                    } else {
                        stringResource(R.string.onboarding_pick_apps_selected_count_plural, state.selectedPackageNames.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (state.installedApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Text(
                            text = "BLOCKED WEBSITES",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.premadeUrlLists, key = { it.id }) { list ->
                        SiteListRow(list = list, onToggle = { enabled -> viewModel.onUrlListToggled(list.id, enabled) })
                    }
                    item {
                        Text(
                            text = "Comprehensive lists, kept current automatically. Turning one on blocks it " +
                                "permanently -- no habit or grace token gets past it, unlike the apps below. " +
                                "Switch that to gated, or build a custom list, later in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "LOCKED APPS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.sortedInstalledApps, key = { it.packageName }) { app ->
                        val checked = app.packageName in state.selectedPackageNames
                        val usageMinutes = state.usageMinutesFor(app)
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = if (state.isRecommended(app)) {
                                {
                                    Text(
                                        // Real screen time backs up the badge whenever it's known
                                        // (the picker's own recommendation signal), rather than
                                        // just asserting "Recommended" with nothing to show for it.
                                        text = if (usageMinutes != null && usageMinutes > 0) {
                                            stringResource(R.string.onboarding_pick_apps_recommended_with_usage, usageMinutes)
                                        } else {
                                            stringResource(R.string.onboarding_pick_apps_recommended)
                                        },
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else if (usageMinutes != null && usageMinutes > 0) {
                                { Text(stringResource(R.string.onboarding_pick_apps_usage_minutes, usageMinutes)) }
                            } else {
                                null
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { viewModel.onAppToggled(app.packageName, it) },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** One premade blanket list's onboarding row -- a switch since it persists, matching every other blanket-list toggle in the app. */
@Composable
private fun SiteListRow(list: UrlBlockList, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(list.name) },
        supportingContent = { Text(if (list.domainCount == 1) "1 domain" else "${list.domainCount} domains") },
        leadingContent = { Icon(Icons.Filled.Public, contentDescription = null) },
        trailingContent = {
            Switch(checked = list.isEnabled, onCheckedChange = onToggle)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
