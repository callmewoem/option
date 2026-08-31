package com.habitsfirst.androidclone.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.ui.components.Heatmap
import com.habitsfirst.androidclone.ui.components.HabitCard
import com.habitsfirst.androidclone.ui.components.accentColor
import com.habitsfirst.androidclone.ui.components.heatmapFractionColor
import com.habitsfirst.androidclone.ui.navigation.LockeBottomBar
import java.time.LocalDate

/**
 * Progress + management, not completion -- doing a habit happens on Home. Every card
 * here opens it for editing, so there's exactly one tap behavior on this whole screen
 * instead of a different action per habit type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    onAddHabit: (HabitKind) -> Unit,
    onEditHabit: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    kind = HabitKind.GATING,
                    title = "Gating",
                    habits = state.gating,
                    onAdd = { onAddHabit(HabitKind.GATING) },
                    onClick = { progress -> onEditHabit(progress.habit.id) },
                )
            }

            item {
                HabitKindSection(
                    kind = HabitKind.TRACKED,
                    title = "Tracked",
                    habits = state.tracked,
                    onAdd = { onAddHabit(HabitKind.TRACKED) },
                    onClick = { progress -> onEditHabit(progress.habit.id) },
                )
            }

            item {
                HabitKindSection(
                    kind = HabitKind.ANTIHABIT,
                    title = "Antihabits",
                    habits = state.antihabits,
                    onAdd = { onAddHabit(HabitKind.ANTIHABIT) },
                    onClick = { progress -> onEditHabit(progress.habit.id) },
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }
}

/** One kind's habits -- [kind]'s accent color on each [HabitCard] is the distinguishing signal, not this header. */
@Composable
private fun HabitKindSection(
    kind: HabitKind,
    title: String,
    habits: List<HabitProgress>,
    onAdd: () -> Unit,
    onClick: (HabitProgress) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(kind.accentColor(), CircleShape),
                )
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        habits.forEach { progress ->
            HabitCard(
                progress = progress,
                kind = kind,
                onClick = { onClick(progress) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
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
