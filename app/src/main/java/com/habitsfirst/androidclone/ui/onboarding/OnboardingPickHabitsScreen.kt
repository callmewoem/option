package com.habitsfirst.androidclone.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.ui.components.icon

@Composable
fun OnboardingPickHabitsScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { OnboardingTopBar(step = 2, totalSteps = 3, onBack = onBack) },
        bottomBar = {
            Button(
                onClick = onContinue,
                enabled = state.canContinueFromHabits,
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
                OnboardingKicker(stringResource(R.string.onboarding_kicker_habits))
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.onboarding_pick_habits_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.onboarding_pick_habits_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.onboarding_pick_habits_gating_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(onboardingHabitTemplates, key = { it.name }) { template ->
                    val checked = template in state.selectedTemplateOrder
                    ListItem(
                        headlineContent = { Text(template.name) },
                        supportingContent = if (template.type.isMeasurable) {
                            { Text("${template.targetValue} ${template.type.unit}/day".trim()) }
                        } else {
                            null
                        },
                        leadingContent = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.onTemplateToggled(template, it) },
                            )
                        },
                        trailingContent = {
                            Icon(template.type.icon(), contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.selectedTemplateOrder.size > 1) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Text(
                                stringResource(R.string.onboarding_pick_habits_ease_in_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.onboarding_pick_habits_ease_in_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(state.selectedTemplateOrder, key = { _, template -> "order-${template.name}" }) { index, template ->
                        ListItem(
                            headlineContent = { Text(template.name) },
                            leadingContent = {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.onTemplateReordered(template, -1) },
                                        enabled = index > 0,
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move easier")
                                    }
                                    IconButton(
                                        onClick = { viewModel.onTemplateReordered(template, 1) },
                                        enabled = index < state.selectedTemplateOrder.lastIndex,
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move harder")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
