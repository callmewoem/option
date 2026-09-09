package com.habitsfirst.androidclone.ui.home

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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.data.repository.EaseInStatus
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.ui.components.BigNumber
import com.habitsfirst.androidclone.ui.components.HabitCard
import com.habitsfirst.androidclone.ui.components.LockeCard
import com.habitsfirst.androidclone.ui.components.LootboxRewardDialog
import com.habitsfirst.androidclone.ui.components.countdownColor
import com.habitsfirst.androidclone.ui.components.formatCountdown
import com.habitsfirst.androidclone.ui.navigation.LockeBottomBar
import com.habitsfirst.androidclone.ui.theme.LockeColor
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
    // Which of the tour's steps is showing -- local only, since the tour is meant to be
    // stepped through in one sitting; onTourDismissed() is what actually persists that
    // it's done, so a process death mid-tour just restarts it rather than losing it.
    var tourStep by remember { mutableIntStateOf(0) }

    // ViewModel.init only fires once for as long as this back-stack entry (and its
    // ViewModel) is retained, so it alone catches a cold start but misses the far more
    // common case of backgrounding Locke and reopening it later -- app-usage and
    // Health-Connect-backed progress would otherwise sit stale until the next 15/30-min
    // periodic tick. Re-running the same refresh on every resume (same pattern as
    // Settings' permission/accountability refreshes) covers both.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDataDrivenHabits() }

    // Everything actionable today, tagged by kind so HabitCard can render its accent --
    // this is the "do it all from Today" list; Stats/To-do are for review and plain tasks.
    val combinedHabits: List<Pair<HabitProgress, HabitKind>> = state.gating.map { it to HabitKind.GATING } +
        state.tracked.map { it to HabitKind.TRACKED } +
        state.antihabits.map { it to HabitKind.ANTIHABIT }

    Scaffold(
        bottomBar = { LockeBottomBar(navController) },
        topBar = {
            TopAppBar(
                title = { Text("Today", style = MaterialTheme.typography.headlineSmall) },
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            // The check-in countdown outranks everything else on the page while it's
            // active (design spec §8) -- the device is effectively unusable for
            // anything else right now, so this goes first, above the streak card.
            if (state.proofOfLifeDue) {
                item {
                    CheckInCountdownBanner(
                        deadlineTime = state.proofOfLifeTime,
                        windowMinutes = state.proofOfLifeWindowMinutes,
                        onClick = onCheckIn,
                    )
                }
            }

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

            if (state.blockedOpenAttemptsToday > 0) {
                item { BlockedAttemptsChip(count = state.blockedOpenAttemptsToday) }
            }

            state.easeInStatus?.let { status ->
                item { EaseInBanner(status) }
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
                Text(
                    text = stringResource(R.string.home_todays_habits),
                    style = MaterialTheme.typography.titleLarge,
                )
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

/**
 * The check-in window's remaining time, at a glance, without opening the lock screen
 * itself (design spec §7, §8) -- the oversized-number countdown treatment, ticking
 * live, escalating verdigris -> brass -> oxide as the deadline nears then passes. Its
 * own card is drawn in enforcement colors even though the rest of Today stays in app
 * mode -- the one exception that signals "something is being demanded of you right
 * now" without switching the whole screen dark.
 */
@Composable
private fun CheckInCountdownBanner(deadlineTime: String, windowMinutes: Int, onClick: () -> Unit) {
    var remainingSeconds by remember(deadlineTime, windowMinutes) {
        mutableIntStateOf(secondsUntilCheckInDeadline(deadlineTime, windowMinutes))
    }
    LaunchedEffect(deadlineTime, windowMinutes) {
        while (true) {
            remainingSeconds = secondsUntilCheckInDeadline(deadlineTime, windowMinutes)
            if (remainingSeconds <= 0) break
            delay(1_000)
        }
    }
    // The window has closed (penalty already applied) -- Today returns to normal rather
    // than showing a stalled 00:00.
    if (remainingSeconds <= 0) return

    val totalWindowSeconds = (windowMinutes * 60).coerceAtLeast(1)
    val fractionElapsed = 1f - (remainingSeconds.toFloat() / totalWindowSeconds)
    val color = countdownColor(fractionElapsed = fractionElapsed, isPastDeadline = false)

    LockeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        containerColor = LockeColor.Iron,
        borderColor = color,
        borderWidth = 1.5.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "CHECK-IN DUE",
                style = MaterialTheme.typography.labelLarge,
                color = LockeColor.OnIronMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            BigNumber(
                value = formatCountdown(remainingSeconds),
                caption = "left to prove you're up, or the block extends",
                color = color,
                captionColor = LockeColor.OnIronMuted,
                size = 48.sp,
            )
        }
    }
}

/** Seconds from now until [deadlineTime] + [windowMinutes] today; 0 once past. */
private fun secondsUntilCheckInDeadline(deadlineTime: String, windowMinutes: Int): Int {
    val target = runCatching { LocalTime.parse(deadlineTime) }.getOrDefault(LocalTime.of(8, 0))
    val deadline = LocalDateTime.of(LocalDate.now(), target).plusMinutes(windowMinutes.toLong())
    val seconds = Duration.between(LocalDateTime.now(), deadline).seconds
    return seconds.coerceAtLeast(0L).toInt()
}

@Composable
private fun BlockedAttemptsChip(count: Int) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        val timesWord = if (count == 1) "time" else "times"
        Text(
            text = "Tried to open a locked app or site $count $timesWord today",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // fill = false keeps this a content-hugging pill on wide screens, but still
            // bounds wrapping to what's actually left after the icon -- otherwise this
            // sentence (which wraps to two lines on narrow phones) gets clipped by the
            // pill's own background/clip on the right edge.
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** One stop on the first-run Today tour: what to notice, and why it's worth knowing. */
private data class TourStep(val title: String, val body: String)

private val HOME_TOUR_STEPS = listOf(
    TourStep(
        title = "Your day at a glance",
        body = "This card tracks what's left today, your streak, and how many apps are " +
            "still locked -- it updates live as you complete habits below.",
    ),
    TourStep(
        title = "Nothing here is fixed",
        body = "Locked apps and habits can be added or removed anytime from Settings -- " +
            "onboarding was just a starting point.",
    ),
    TourStep(
        title = "Finish for a reward",
        body = "Complete every gating habit in a day and Locke opens a daily lootbox -- " +
            "tokens, or a kept cosmetic.",
    ),
)

@Composable
private fun HomeTourBanner(step: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val current = HOME_TOUR_STEPS[step]
    LockeCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
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
    LockeCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val dayWord = if (remaining == 1) "day" else "days"
            Text(
                text = "$remaining more $dayWord on \"${status.activeHabitName}\" unlocks \"${status.nextHabitName}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    LockeCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Try photo verification", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                }
            }
            Text(
                text = "One habit can be checked by AI instead of the honor system -- describe what a " +
                    "proof photo should show and Locke verifies it for you. Set one up now, or skip it -- " +
                    "it's always available later from Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSetUp) { Text("Set it up") }
            }
        }
    }
}

/**
 * The hero of Today: what's left, at a glance -- the single strongest lever on this
 * screen, since it's the first thing seen on every open. The streak's flame is brass
 * (earned), the lock chip and progress fill are verdigris (structural).
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
    val onCard = if (allDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val onCardMuted = if (allDone) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    LockeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (allDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (allDone) {
                Text(
                    text = stringResource(R.string.home_all_done_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = onCard,
                )
                Text(
                    text = stringResource(R.string.home_all_done_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCardMuted,
                )
            } else {
                BigNumber(
                    value = "${total - completed}",
                    caption = if (total - completed == 1) "habit left today" else "habits left today",
                    color = onCard,
                    captionColor = onCardMuted,
                    size = 44.sp,
                )
            }

            if (!allDone && total > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Butt,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(LockeColor.Brass.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = LockeColor.Brass,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$streakDays",
                        style = MaterialTheme.typography.headlineSmall,
                        color = onCard,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(if (streakDays == 1) R.string.home_streak_day_singular else R.string.home_streak_day_plural),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onCardMuted,
                    )
                }
                LockStatusChip(allDone = allDone, lockedAppCount = lockedAppCount)
            }
        }
    }
}

/** The small pill on [SummaryCard] showing how many apps are locked right now -- open or shut, at a glance. Verdigris throughout: locking/unlocking is structural, not a reward or a cost. */
@Composable
private fun LockStatusChip(allDone: Boolean, lockedAppCount: Int) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (allDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest,
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
    LockeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onAddHabit,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
