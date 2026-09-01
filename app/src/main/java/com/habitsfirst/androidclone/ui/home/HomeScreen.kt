package com.habitsfirst.androidclone.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.data.repository.EaseInStatus
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.Todo
import com.habitsfirst.androidclone.ui.components.HabitCard
import com.habitsfirst.androidclone.ui.components.LootboxRewardDialog
import com.habitsfirst.androidclone.ui.navigation.LockeBottomBar
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onVerifyHabit: (Long) -> Unit,
    onCheckIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onManageApps: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wonReward by viewModel.wonReward.collectAsStateWithLifecycle()
    var progressDialogTarget by remember { mutableStateOf<HabitProgress?>(null) }
    var newTodoText by remember { mutableStateOf("") }
    var newTodoRepeatDays by remember { mutableStateOf<Set<DayOfWeek>>(emptySet()) }

    // Everything actionable today, tagged by kind so HabitCard can render its accent --
    // this is the "do it all from Home" list; Habits/Todos are for managing the lists.
    val combinedHabits: List<Pair<HabitProgress, HabitKind>> = state.gating.map { it to HabitKind.GATING } +
        state.tracked.map { it to HabitKind.TRACKED } +
        state.antihabits.map { it to HabitKind.ANTIHABIT }

    Scaffold(
        bottomBar = { LockeBottomBar(navController) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(greetingRes())) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddHabit,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_add_habit)) },
                containerColor = FloatingActionButtonDefaults.containerColor,
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummaryCard(
                    completed = state.completedCount,
                    total = state.totalCount,
                    streakDays = state.streakDays,
                    lockedAppCount = state.blockedApps.count { it.isEnabled },
                    allDone = state.allDone,
                )
            }

            state.easeInStatus?.let { status ->
                item { EaseInBanner(status) }
            }

            if (state.proofOfLifeDue) {
                item { ProofOfLifeBanner(onClick = onCheckIn) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_todays_habits),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onManageApps) {
                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.home_manage_apps))
                    }
                }
            }

            if (combinedHabits.isEmpty()) {
                item { EmptyHabitsCard(onAddHabit) }
            } else {
                items(combinedHabits, key = { (progress, kind) -> "${kind.name}-${progress.habit.id}" }) { (progress, kind) ->
                    HabitCard(
                        progress = progress,
                        kind = kind,
                        onClick = {
                            when {
                                kind == HabitKind.ANTIHABIT ->
                                    viewModel.onToggleAntihabitSlip(progress.habit.id, progress.habit.name, !progress.isCompleted)
                                progress.habit.type == HabitType.CUSTOM ->
                                    viewModel.onCustomHabitToggled(progress.habit.id, !progress.isCompleted)
                                progress.habit.type == HabitType.MEDITATION_MINUTES -> onOpenHabit(progress.habit.id)
                                progress.habit.type == HabitType.IMAGE_VERIFICATION -> onVerifyHabit(progress.habit.id)
                                else -> progressDialogTarget = progress
                            }
                        },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.todos_title), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        label = { Text(stringResource(R.string.todos_add_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.onAddTodo(newTodoText, newTodoRepeatDays)
                            newTodoText = ""
                            newTodoRepeatDays = emptySet()
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                RepeatDaysPicker(
                    selectedDays = newTodoRepeatDays,
                    onToggleDay = { day ->
                        newTodoRepeatDays = if (day in newTodoRepeatDays) {
                            newTodoRepeatDays - day
                        } else {
                            newTodoRepeatDays + day
                        }
                    },
                )
            }

            items(state.pendingTodos, key = { "todo-${it.id}" }) { todo ->
                QuickTodoRow(
                    todo = todo,
                    onToggle = { viewModel.onToggleTodoDone(todo) },
                    onDelete = { viewModel.onDeleteTodo(todo) },
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

/** Lets the user pick which days of the week a new todo recurs on; none selected means one-off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatDaysPicker(selectedDays: Set<DayOfWeek>, onToggleDay: (DayOfWeek) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DayOfWeek.values().forEach { day ->
            FilterChip(
                selected = day in selectedDays,
                onClick = { onToggleDay(day) },
                label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
            )
        }
    }
}

@Composable
private fun QuickTodoRow(todo: Todo, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                )
                if (todo.isRecurring) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatRepeatDays(todo.repeatDays),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.todos_delete))
            }
        }
    }
}

/** e.g. "Every Sun", "Every Mon, Wed, Fri", or "Every day" when all seven are set. */
private fun formatRepeatDays(days: Set<DayOfWeek>): String {
    if (days.size == 7) return "Every day"
    val ordered = DayOfWeek.values().filter { it in days }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    return "Every $ordered"
}

@Composable
private fun EaseInBanner(status: EaseInStatus) {
    val remaining = (status.requiredStreak - status.activeHabitStreak).coerceAtLeast(0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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
            Icon(Icons.Filled.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            val dayWord = if (remaining == 1) "day" else "days"
            Text(
                text = "$remaining more $dayWord on \"${status.activeHabitName}\" unlocks \"${status.nextHabitName}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ProofOfLifeBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
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
            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Morning check-in due",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Tap to prove you're up before apps stay locked longer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    completed: Int,
    total: Int,
    streakDays: Int,
    lockedAppCount: Int,
    allDone: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (allDone) {
                    stringResource(R.string.home_all_done_title)
                } else {
                    stringResource(R.string.home_remaining_habits, total - completed, total)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.home_streak_days, streakDays),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = if (allDone) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$lockedAppCount locked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHabitsCard(onAddHabit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onAddHabit,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "No habits yet", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun greetingRes(): Int {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> R.string.home_greeting_morning
        hour < 18 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
}
