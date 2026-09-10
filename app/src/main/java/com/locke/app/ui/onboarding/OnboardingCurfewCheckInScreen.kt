package com.locke.app.ui.onboarding

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locke.app.R
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.data.repository.ProofOfLifeRepository
import com.locke.app.ui.components.LockeCard
import com.locke.app.ui.components.LockePrimaryButton

/**
 * Onboarding's fifth step: curfew and the morning check-in. Everything here defaults
 * off and can be changed anytime from Settings. The morning check-in's photo is checked
 * by the same AI verification as photo-verification habits -- free on a monthly
 * allowance, unlimited on Premium (see `OnboardingPaywallScreen`, the step right after
 * this one) -- disclosed here rather than sprung on the person the first time they try
 * to use it (design spec §9, onboarding screen 7).
 */
@Composable
fun OnboardingCurfewCheckInScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { OnboardingTopBar(step = 5, totalSteps = 6, onBack = onBack) },
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                LockePrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            OnboardingKicker("Part five -- the schedule")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Set the schedule", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Two more optional pieces: a hard bedtime curfew, and a morning check-in that " +
                    "proves you're actually up. Both are off by default -- set them now, or skip and " +
                    "turn them on later from Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            LockeCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bedtime curfew", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "A hard window -- no habit or grace token unlocks a locked app during it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.bedtimeEnabled,
                            onCheckedChange = { viewModel.onBedtimeChanged(it, state.bedtimeStart, state.bedtimeEnd) },
                        )
                    }
                    if (state.bedtimeEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.bedtimeStart,
                                onValueChange = { viewModel.onBedtimeChanged(true, it, state.bedtimeEnd) },
                                label = { Text("Start (HH:mm)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.bedtimeEnd,
                                onValueChange = { viewModel.onBedtimeChanged(true, state.bedtimeStart, it) },
                                label = { Text("End (HH:mm)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            LockeCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Morning check-in", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "A photo proving you're up, checked by the same AI as photo-verification " +
                                    "habits (${PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH} free checks a month -- " +
                                    "see the next step). Miss the window and the block extends " +
                                    "${ProofOfLifeRepository.PENALTY_MINUTES} minutes -- priced exactly, every time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.proofOfLifeEnabled,
                            onCheckedChange = {
                                viewModel.onProofOfLifeChanged(it, state.proofOfLifeTime, state.proofOfLifeWindowMinutes)
                            },
                        )
                    }
                    if (state.proofOfLifeEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.proofOfLifeTime,
                            onValueChange = {
                                viewModel.onProofOfLifeChanged(true, it, state.proofOfLifeWindowMinutes)
                            },
                            label = { Text("Time (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
