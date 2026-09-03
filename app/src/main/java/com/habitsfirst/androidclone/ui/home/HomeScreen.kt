package com.habitsfirst.androidclone.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.habitsfirst.androidclone.util.DateProvider
import java.time.LocalTime

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
    onSetUpPhotoVerification: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wonReward by viewModel.wonReward.collectAsStateWithLifecycle()
    var progressDialogTarget by remember { mutableStateOf<HabitProgress?>(null) }
    var newTodoText by remember { mutableStateOf("") }
    var newTodoDueTomorrow by remember { mutableStateOf(false) }
    // Which of the tour's steps is showing -- local only, since the tour is meant to be
    // stepped through in one sitting; onTourDismissed() is what actually persists that
    // it's done, so a process death mid-tour just restarts it rather than losing it.
    var tourStep by remember { mutableIntStateOf(0) }

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
                    IconButton(
                        onClick = viewModel::refreshDataDrivenHabits,
                        enabled = !state.isRefreshingDataDrivenHabits,
                    ) {
                        if (state.isRefreshingDataDrivenHabits) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.home_refresh_data_driven_habits),
                            )
                        }
                    }
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
            if (state.showTour) {
                item {
                    HomeTourBanner(
                        step = tourStep,
                        onNext = {
                            if (tourStep < HOME_TOUR_STEPS.lastIndex) {
                                tourStep++
                            } else {
                                viewModel.onTourDismissed()
                            }
                        },
                        onSkip = viewModel::onTourDismissed,
                    )
                }
            }

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

            if (state.showPhotoVerificationPrompt) {
                item {
                    PhotoVerificationPromptBanner(
                        onSetUp = {
                            viewModel.onPhotoVerificationPromptDismissed()
                            onSetUpPhotoVerification()
                        },
                        onDismiss = viewModel::onPhotoVerificationPromptDismissed,
                    )
                }
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
                                progress.habit.type == HabitType.PHOTO -> onVerifyHabit(progress.habit.id)
                                progress.habit.type == HabitType.TALLY ->
                                    viewModel.onTallyHabitToggled(progress.habit.id, !progress.isCompleted)
                                progress.habit.type == HabitType.TIMED_MINUTES -> onOpenHabit(progress.habit.id)
                                // Tracked automatically -- no manual correction, tapping does nothing.
                                progress.habit.type == HabitType.APP_USAGE_MINUTES -> {}
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
                            viewModel.onAddTodo(newTodoText, newTodoDueTomorrow)
                            newTodoText = ""
                            newTodoDueTomorrow = false
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                DueDatePicker(
                    dueTomorrow = newTodoDueTomorrow,
                    onDueTomorrowChanged = { newTodoDueTomorrow = it },
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

/** Lets the user pick whether a new todo is due today or tomorrow -- todos aren't day-dependent beyond that. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(dueTomorrow: Boolean, onDueTomorrowChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = !dueTomorrow,
            onClick = { onDueTomorrowChanged(false) },
            label = { Text("Today") },
        )
        FilterChip(
            selected = dueTomorrow,
            onClick = { onDueTomorrowChanged(true) },
            label = { Text("Tomorrow") },
        )
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
                if (!DateProvider.isToday(todo.date)) {
                    Text(
                        text = "Tomorrow",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.todos_delete))
            }
        }
    }
}

/** One stop on the first-run Home tour: what to notice, and why it's worth knowing. */
private data class TourStep(val title: String, val body: String)

private val HOME_TOUR_STEPS = listOf(
    TourStep(
        title = "Your day at a glance",
        body = "This card tracks what's left today, your streak, and how many apps are " +
            "still locked -- it updates live as you complete habits below.",
    ),
    TourStep(
        title = "Nothing here is fixed",
        body = "Tap the lock icon above to add or remove locked apps anytime, and the + " +
            "button to add a new habit -- onboarding was just a starting point.",
    ),
    TourStep(
        title = "Finish for a reward",
        body = "Complete every gating habit in a day and Locke opens a daily lootbox -- " +
            "grace tokens, task-skip tokens, or a new theme accent.",
    ),
)

/**
 * A short, dismissible spotlight tour shown once on Home after onboarding -- same banner
 * pattern as [EaseInBanner] and [ProofOfLifeBanner] rather than an overlay, so it reads as
 * one more card in the list instead of blocking anything underneath it.
 */
@Composable
private fun HomeTourBanner(step: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val current = HOME_TOUR_STEPS[step]
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${step + 1}/${HOME_TOUR_STEPS.size} -- ${current.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSkip) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Skip tour",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                text = current.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNext) {
                    Text(if (step < HOME_TOUR_STEPS.lastIndex) "Next" else "Got it")
                }
            }
        }
    }
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

/**
 * A one-day-only nudge toward the flagship habit type: it needs a description and/or
 * example photo, so it doesn't fit onboarding's quick-pick template list -- this is
 * the deep link into setting one up instead, offered once and then left alone.
 */
@Composable
private fun PhotoVerificationPromptBanner(onSetUp: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Try photo verification",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                text = "One habit can be checked by AI instead of the honor system -- describe what a " +
                    "proof photo should show and Locke verifies it for you. Set one up now, or skip it -- " +
                    "it's always available later from Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSetUp) { Text("Set it up") }
            }
        }
    }
}

/**
 * The hero of Home: today's status at a glance -- a big monospace streak readout (this
 * app's numbers are always stamped-label-style, never friendly UI type), how many
 * gating habits are left, and how many apps are still locked. The single strongest
 * "modern and intuitive" lever on this screen, since it's the first thing seen on
 * every open and the thing every other card on the page supports.
 */
@Composable
private fun SummaryCard(
    completed: Int,
    total: Int,
    streakDays: Int,
    lockedAppCount: Int,
    allDone: Boolean,
) {
    val fraction = if (total > 0) completed.toFloat() / total else 1f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (allDone) {
                            stringResource(R.string.home_all_done_title)
                        } else {
                            stringResource(R.string.home_remaining_habits, total - completed, total)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (allDone) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (allDone) {
                        Text(
                            text = stringResource(R.string.home_all_done_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                LockStatusChip(allDone = allDone, lockedAppCount = lockedAppCount)
            }

            if (!allDone && total > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (allDone) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$streakDays",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (allDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(if (streakDays == 1) R.string.home_streak_day_singular else R.string.home_streak_day_plural),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (allDone) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** The small pill on [SummaryCard] showing how many apps are locked right now -- open or shut, at a glance. */
@Composable
private fun LockStatusChip(allDone: Boolean, lockedAppCount: Int) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (allDone) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (allDone) Icons.Filled.LockOpen else Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$lockedAppCount",
            style = MaterialTheme.typography.labelLarge,
            color = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyHabitsCard(onAddHabit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onAddHabit,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = "No habits yet", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Add the first one that gates your locked apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
