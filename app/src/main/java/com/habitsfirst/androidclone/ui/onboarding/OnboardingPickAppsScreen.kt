package com.habitsfirst.androidclone.ui.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R

@Composable
fun OnboardingPickAppsScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { OnboardingTopBar(step = 1, totalSteps = 3, onBack = onBack) },
        bottomBar = {
            Button(
                onClick = onContinue,
                enabled = state.canContinueFromApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(stringResource(R.string.onboarding_continue))
            }
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
                    items(state.installedApps, key = { it.packageName }) { app ->
                        val checked = app.packageName in state.selectedPackageNames
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = if (state.isRecommended(app)) {
                                { Text("Recommended", color = MaterialTheme.colorScheme.primary) }
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
