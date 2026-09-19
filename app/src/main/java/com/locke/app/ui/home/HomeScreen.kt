package com.locke.app.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.locke.app.R
import com.locke.app.data.repository.EaseInStatus
import com.locke.app.domain.model.HabitKind
import com.locke.app.domain.model.HabitProgress
import com.locke.app.domain.model.HabitType
import com.locke.app.ui.components.BigNumber
import com.locke.app.ui.components.HabitPill
import com.locke.app.ui.components.LockeCard
import com.locke.app.ui.components.LootboxRewardDialog
import com.locke.app.ui.components.RoundIconButton
import com.locke.app.ui.components.TodayHero
import com.locke.app.ui.components.TodayProgressRow
import com.locke.app.ui.components.countdownColor
import com.locke.app.ui.components.formatCountdown
import com.locke.app.ui.components.habitPillIconTint
import com.locke.app.ui.components.habitPillSubtitle
import com.locke.app.ui.components.todayIconRes
import com.locke.app.ui.navigation.LockeBottomBar
import com.locke.app.ui.theme.LockeColor
import com.locke.app.ui.theme.SpaceGrotesk
import com.locke.app.ui.theme.spaceMono
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
    onScanTag: (Long) -> Unit,
    onCheckIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onManageApps: () -> Unit,
    onSetUpPhotoVerification: () -> Unit,
    onEditHabit: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wonReward by viewModel.wonReward.collectAsStateWithLifecycle()
    var progressDialogTarget by remember { mutableStateOf<HabitProgress?>(null) }
    // Which of the tour's steps is showing -- local only, since the tour is meant to be
    // stepped through in one sitting; onTourDismissed() is what actually persists that
    // it's done, so a process death mid-tour just restarts it rather than losing it.
    var tourStep by remember { mutableIntStateOf(0) }
    var showLockoutSheet by remember { mutableStateOf(false) }

    // ViewModel.init only fires once for as long as this back-stack entry (and its
    // ViewModel) is retained, so it alone catches a cold start but misses the far more
    // common case of backgrounding Locke and reopening it later -- app-usage and
    // Health-Connect-backed progress would otherwise sit stale until the next 15/30-min
    // periodic tick. Re-running the same refresh on every resume (same pattern as
    // Settings' permission/accountability refreshes) covers both.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDataDrivenHabits() }

    // Everything actionable today, tagged by kind so each row can render its accent --
    // this is the "do it all from Today" list; Stats/To-do are for review and plain tasks.
    val positiveHabits: List<Pair<HabitProgress, HabitKind>> = state.gating.map { it to HabitKind.GATING } +
        state.tracked.map { it to HabitKind.TRACKED }
    val avoidHabits: List<Pair<HabitProgress, HabitKind>> = state.antihabits.map { it to HabitKind.ANTIHABIT }
    val allHabitsEmpty = positiveHabits.isEmpty() && avoidHabits.isEmpty()

    Scaffold(
        containerColor = LockeColor.Bone,
        topBar = { TodayHeader(onOpenSettings = onOpenSettings) },
        bottomBar = { LockeBottomBar(navController) },
        floatingActionButton = {
            // The design's progress row already carries a lockout button and an add-habit
            // button (design spec §3) -- this FAB now only covers the one Today action
            // with no equivalent there: managing which apps are blocked.
            FloatingActionButton(onClick = onManageApps, containerColor = LockeColor.Moss, contentColor = LockeColor.BoneIn) {
                Icon(Icons.Filled.Apps, contentDescription = stringResource(R.string.home_quick_actions))
            }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The check-in countdown outranks everything else on the page while it's
            // active (design spec §8) -- the device is effectively unusable for
            // anything else right now, so this goes first, above the hero.
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
                TodayHero(
                    habitsLeft = state.totalCount - state.completedCount,
                    streakDays = state.streakDays,
                    dayScores = state.dayScores,
                    todayFraction = if (state.totalCount > 0) state.completedCount.toFloat() / state.totalCount else 0f,
                )
            }

            item {
                TodayProgressRow(
                    completed = state.completedCount,
                    total = state.totalCount,
                    onLockout = { showLockoutSheet = true },
                    onAddHabit = onAddHabit,
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

            if (allHabitsEmpty) {
                item { EmptyHabitsCard(onAddHabit) }
            } else {
                item {
                    TodayHabitSection(
                        rows = positiveHabits,
                        onToggle = { progress, kind -> onHabitToggled(progress, kind, viewModel) { progressDialogTarget = it } },
                        onEdit = onEditHabit,
                        onDelete = viewModel::onDeleteHabit,
                        onReorder = viewModel::onReorderHabits,
                        onOpenTimed = onOpenHabit,
                        onVerifyHabit = onVerifyHabit,
                        onScanTag = onScanTag,
                    )
                }
                if (avoidHabits.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 1.dp,
                            color = LockeColor.HabitBorder,
                        )
                    }
                    item {
                        TodayHabitSection(
                            rows = avoidHabits,
                            onToggle = { progress, kind -> onHabitToggled(progress, kind, viewModel) { progressDialogTarget = it } },
                            onEdit = onEditHabit,
                            onDelete = viewModel::onDeleteHabit,
                            onReorder = viewModel::onReorderHabits,
                            onOpenTimed = onOpenHabit,
                            onVerifyHabit = onVerifyHabit,
                            onScanTag = onScanTag,
                        )
                    }
                }
                item {
                    Text(
                        text = "that's all for today",
                        style = spaceMono(11.sp),
                        color = LockeColor.MutedTextFaint,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
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

    if (showLockoutSheet) {
        LockoutDialog(
            lockoutUntilEpochMillis = state.lockoutUntilEpochMillis,
            onStart = { minutes ->
                viewModel.onStartLockout(minutes)
                showLockoutSheet = false
            },
            onCancelLockout = {
                viewModel.onCancelLockout()
                showLockoutSheet = false
            },
            onDismiss = { showLockoutSheet = false },
        )
    }
}

/** Dispatches a row tap to the right action for its habit type -- same per-type routing Today has always used, just factored out so [HomeScreen] and previews share it. */
private fun onHabitToggled(
    progress: HabitProgress,
    kind: HabitKind,
    viewModel: HomeViewModel,
    onNeedsProgressDialog: (HabitProgress) -> Unit,
) {
    when {
        kind == HabitKind.ANTIHABIT ->
            viewModel.onToggleAntihabitSlip(progress.habit.id, progress.habit.name, !progress.isCompleted)
        progress.habit.type == HabitType.TALLY ||
            progress.habit.type == HabitType.VISIT_LOCATION ||
            progress.habit.type == HabitType.GITHUB_CONTRIBUTION ->
            viewModel.onTallyHabitToggled(progress.habit.id, !progress.isCompleted)
        // Photo, timer and tag-scan types open their own flow (see HomeScreen's onClick
        // routing below); app-usage is tracked automatically; anything else measurable
        // opens the log-progress dialog.
        else -> onNeedsProgressDialog(progress)
    }
}

/** Header: leaf logo + "habits" wordmark, settings gear -- design spec §3, kept identical on every app-mode screen. */
@Composable
internal fun TodayHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_leaf_logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "habits",
                style = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.2).sp),
                color = LockeColor.Ink,
            )
        }
        RoundIconButton(
            iconRes = R.drawable.ic_gear,
            contentDescription = stringResource(R.string.settings_title),
            onClick = onOpenSettings,
            iconSize = 18.dp,
        )
    }
}

/**
 * One reorderable, swipeable section of the Today list (design spec §4/§6): positive
 * habits and avoid habits are separate drag groups, so a reorder can't cross the divider
 * between them. Drag state is local to this composable -- [orderedRows] tracks the live
 * visual order while dragging, resynced from [rows] whenever nothing is being dragged;
 * [onReorder] persists the final order via [com.locke.app.data.repository.HabitRepository.reorderHabits].
 */
@Composable
internal fun TodayHabitSection(
    rows: List<Pair<HabitProgress, HabitKind>>,
    onToggle: (HabitProgress, HabitKind) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onOpenTimed: (Long) -> Unit,
    onVerifyHabit: (Long) -> Unit,
    onScanTag: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val stepPx = with(density) { 50.dp.toPx() }

    var orderedRows by remember { mutableStateOf(rows) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragSteps by remember { mutableIntStateOf(0) }

    LaunchedEffect(rows) {
        if (draggingId == null) orderedRows = rows
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        orderedRows.forEach { (progress, kind) ->
            val habit = progress.habit
            key(habit.id) {
                HabitPill(
                    title = habit.name,
                    subtitle = habitPillSubtitle(progress),
                    iconRes = if (kind == HabitKind.ANTIHABIT) R.drawable.ic_habit_no_entry else habit.type.todayIconRes(),
                    iconTint = habitPillIconTint(kind, progress.isCompleted),
                    isCompleted = progress.isCompleted,
                    isAvoid = kind == HabitKind.ANTIHABIT,
                    onToggleComplete = {
                        when {
                            kind != HabitKind.ANTIHABIT && habit.type == HabitType.PHOTO -> onVerifyHabit(habit.id)
                            kind != HabitKind.ANTIHABIT && habit.type == HabitType.TAG_SCAN -> onScanTag(habit.id)
                            kind != HabitKind.ANTIHABIT && habit.type == HabitType.TIMED_MINUTES -> onOpenTimed(habit.id)
                            kind != HabitKind.ANTIHABIT && habit.type == HabitType.APP_USAGE_MINUTES -> Unit
                            else -> onToggle(progress, kind)
                        }
                    },
                    onEdit = { onEdit(habit.id) },
                    onDelete = { onDelete(habit.id) },
                    isBeingDragged = draggingId == habit.id,
                    dragOffsetPx = dragOffsetPx,
                    onDragStart = {
                        draggingId = habit.id
                        dragOffsetPx = 0f
                        dragSteps = 0
                    },
                    onDragDelta = { deltaY ->
                        dragOffsetPx += deltaY
                        val steps = (dragOffsetPx / stepPx).let { if (it >= 0) kotlin.math.floor(it) else kotlin.math.ceil(it) }.toInt()
                        if (steps != dragSteps) {
                            val currentIndex = orderedRows.indexOfFirst { it.first.habit.id == draggingId }
                            val targetIndex = (currentIndex + (steps - dragSteps)).coerceIn(0, orderedRows.lastIndex)
                            if (targetIndex != currentIndex && currentIndex >= 0) {
                                orderedRows = orderedRows.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
                            }
                            dragSteps = steps
                        }
                    },
                    onDragEnd = {
                        onReorder(orderedRows.map { it.first.habit.id })
                        draggingId = null
                        dragOffsetPx = 0f
                        dragSteps = 0
                    },
                )
            }
        }
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
        modifier = Modifier.fillMaxWidth(),
        containerColor = LockeColor.Iron,
        borderColor = color,
        borderWidth = 1.5.dp,
        onClick = onClick,
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
            .background(LockeColor.BoneIn)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock_round),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = LockeColor.MutedText,
        )
        Spacer(modifier = Modifier.width(6.dp))
        val timesWord = if (count == 1) "time" else "times"
        Text(
            text = "Tried to open a locked app or site $count $timesWord today",
            style = MaterialTheme.typography.labelMedium,
            color = LockeColor.MutedText,
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
                    "proof photo should show and Locke verifies it for you. A few checks are free every " +
                    "month, unlimited on Premium. Set one up now, or skip it -- it's always available " +
                    "later from Settings.",
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
