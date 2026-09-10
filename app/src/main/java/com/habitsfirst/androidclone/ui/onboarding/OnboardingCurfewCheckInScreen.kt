package com.habitsfirst.androidclone.ui.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.data.repository.ProofOfLifeRepository
import com.habitsfirst.androidclone.ui.components.LockeCard
import com.habitsfirst.androidclone.ui.components.LockePrimaryButton

/**
 * Onboarding's fifth and final step: curfew and the morning check-in, with the
 * Anthropic API-key dependency disclosed right here -- not sprung on the person the
 * first time they try to use photo verification (design spec §9, onboarding screen 7).
 * Everything here defaults off and can be changed anytime from Settings.
 */
@Composable
fun OnboardingCurfewCheckInScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var apiKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinish()
    }

    Scaffold(
        topBar = { OnboardingTopBar(step = 5, totalSteps = 5, onBack = onBack) },
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                LockePrimaryButton(
                    text = stringResource(if (viewModel.isReplay) R.string.onboarding_replay_done else R.string.onboarding_finish),
                    onClick = { viewModel.finishOnboarding() },
                    enabled = !state.isFinishing,
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
            OnboardingKicker("Part five: the schedule")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Set the schedule", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Two more optional pieces: a hard bedtime curfew, and a morning check-in that " +
                    "proves you're up. Both are off by default. Skip them now and turn them on " +
                    "later from Settings.",
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
                                "No habit or grace token unlocks a locked app during this window.",
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
                                "A photo proving you're up, checked the same way as photo-verification " +
                                    "habits. Miss the window and the block extends " +
                                    "${ProofOfLifeRepository.PENALTY_MINUTES} minutes.",
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
            Spacer(modifier = Modifier.height(12.dp))

            LockeCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Anthropic API key", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Needed for photo verification and the morning check-in above. Without it, both " +
                            "just show \"can't reach the checker.\" Photos are sent to Anthropic's API only " +
                            "when checked. You can add this later from Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.anthropicApiKey,
                        onValueChange = viewModel::onAnthropicApiKeyChanged,
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (apiKeyVisible) "Hide key" else "Show key",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
