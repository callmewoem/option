package com.habitsfirst.androidclone.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.ui.components.Heatmap
import com.habitsfirst.androidclone.ui.components.HabitCard
import com.habitsfirst.androidclone.ui.components.LootboxRewardDialog
import com.habitsfirst.androidclone.ui.components.heatmapFractionColor
import com.habitsfirst.androidclone.ui.home.LogProgressDialog
import com.habitsfirst.androidclone.ui.navigation.LockeBottomBar
import com.habitsfirst.androidclone.ui.navigation.Screen
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    onAddHabit: (HabitKind) -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wonReward by viewModel.wonReward.collectAsStateWithLifecycle()
    var progressDialogTarget by remember { mutableStateOf<HabitProgress?>(null) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Habits") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(),
            )
        },
        bottomBar = { LockeBottomBar(navController) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Last 20 weeks", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                val today = LocalDate.now()
                // Canvas draws in a DrawScope, not a composable context, so these have
                // to be resolved here and captured by value, not read inside colorForDate.
                val primary = MaterialTheme.colorScheme.primary
                val secondary = MaterialTheme.colorScheme.secondary
                val emptyColor = MaterialTheme.colorScheme.surfaceVariant
                Heatmap(
                    startDate = today.minusWeeks(20),
                    endDate = today,
                    colorForDate = { date ->
                        val score = state.dayScores[date]
                        when {
                            date in state.goldStarDates -> secondary
                            score == null -> emptyColor
                            else -> heatmapFractionColor(score, primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                HabitKindSection(
                    title = "Gating",
                    subtitle = "Must be done today or your apps stay locked.",
                    habits = state.gating,
                    onAdd = { onAddHabit(HabitKind.GATING) },
                    onClick = { progress ->
                        when (progress.habit.type) {
                            HabitType.CUSTOM -> viewModel.onCustomHabitToggled(progress.habit.id, !progress.isCompleted)
                            HabitType.MEDITATION_MINUTES -> onOpenHabit(progress.habit.id)
                            else -> progressDialogTarget = progress
                        }
                    },
                )
            }

            item {
                HabitKindSection(
                    title = "Tracked",
                    subtitle = "Logged on your heatmap. Never blocks anything.",
                    habits = state.tracked,
                    onAdd = { onAddHabit(HabitKind.TRACKED) },
                    onClick = { progress ->
                        when (progress.habit.type) {
                            HabitType.CUSTOM -> viewModel.onCustomHabitToggled(progress.habit.id, !progress.isCompleted)
                            HabitType.MEDITATION_MINUTES -> onOpenHabit(progress.habit.id)
                            else -> progressDialogTarget = progress
                        }
                    },
                )
            }

            item {
                AntihabitSection(
                    habits = state.antihabits,
                    onAdd = { onAddHabit(HabitKind.ANTIHABIT) },
                    onToggleSlip = { progress, logged ->
                        viewModel.onToggleAntihabitSlip(progress.habit.id, progress.habit.name, logged)
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }

    progressDialogTarget?.let { progress ->
        LogProgressDialog(
            progress = progress,
            onDismiss = { progressDialogTarget = null },
            onConfirm = { newValue ->
                viewModel.onLogProgress(progress.habit.id, progress.habit.targetValue, newValue)
                progressDialogTarget = null
            },
        )
    }

    wonReward?.let { reward ->
        LootboxRewardDialog(reward = reward, onDismiss = viewModel::onRewardDismissed)
    }
}

@Composable
private fun HabitKindSection(
    title: String,
    subtitle: String,
    habits: List<HabitProgress>,
    onAdd: () -> Unit,
    onClick: (HabitProgress) -> Unit,
) {
    Column {
        SectionHeader(title = title, subtitle = subtitle, onAdd = onAdd)
        Spacer(modifier = Modifier.height(8.dp))
        habits.forEach { progress ->
            HabitCard(progress = progress, onClick = { onClick(progress) }, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (habits.isEmpty()) {
            Text(
                "None yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AntihabitSection(
    habits: List<HabitProgress>,
    onAdd: () -> Unit,
    onToggleSlip: (HabitProgress, Boolean) -> Unit,
) {
    Column {
        SectionHeader(
            title = "Antihabits",
            subtitle = "Silence is success. Log it only when you slip.",
            onAdd = onAdd,
        )
        Spacer(modifier = Modifier.height(8.dp))
        habits.forEach { progress ->
            val slipped = progress.isCompleted
            Card(
                onClick = { onToggleSlip(progress, !slipped) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (slipped) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = if (slipped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(progress.habit.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (slipped) "Slipped today -- apps locked a little longer." else "Clean today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (habits.isEmpty()) {
            Text(
                "None yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add")
        }
    }
}
