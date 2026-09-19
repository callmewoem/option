package com.locke.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.locke.app.domain.model.Habit
import com.locke.app.domain.model.HabitKind
import com.locke.app.domain.model.HabitProgress
import com.locke.app.domain.model.HabitType
import com.locke.app.ui.components.TodayHero
import com.locke.app.ui.components.TodayProgressRow
import com.locke.app.ui.navigation.LockeBottomBar
import com.locke.app.ui.theme.LockeColor
import com.locke.app.ui.theme.LockeTheme
import com.locke.app.ui.theme.spaceMono

/**
 * The Today screen's redesigned pieces, previewed without a real ViewModel -- the
 * check-in/tour/ease-in/photo-verification banners are ViewModel-driven conditionals
 * unrelated to this redesign, so previews focus on header/hero/progress/list/footer/nav.
 */
@Composable
internal fun TodayScreenPreviewScaffold(rows: List<Pair<HabitProgress, HabitKind>>) {
    val positive = rows.filter { it.second != HabitKind.ANTIHABIT }
    val avoid = rows.filter { it.second == HabitKind.ANTIHABIT }
    val completed = positive.count { it.first.isCompleted }
    val total = positive.size
    val navController = rememberNavController()

    Scaffold(
        containerColor = LockeColor.Bone,
        topBar = { TodayHeader(onOpenSettings = {}) },
        bottomBar = { LockeBottomBar(navController) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TodayHero(
                    habitsLeft = total - completed,
                    streakDays = 3,
                    dayScores = emptyMap(),
                    todayFraction = if (total > 0) completed.toFloat() / total else 0f,
                )
            }
            item {
                TodayProgressRow(completed = completed, total = total, onLockout = {}, onAddHabit = {})
            }
            if (rows.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No habits yet -- tap + to add one.",
                            style = spaceMono(12.sp),
                            color = LockeColor.MutedTextFaint,
                        )
                    }
                }
            } else {
                item {
                    TodayHabitSection(
                        rows = positive,
                        onToggle = { _, _ -> },
                        onEdit = {},
                        onDelete = {},
                        onReorder = {},
                        onOpenTimed = {},
                        onVerifyHabit = {},
                        onScanTag = {},
                    )
                }
                if (avoid.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 1.dp,
                            color = LockeColor.HabitBorder,
                        )
                    }
                    item {
                        TodayHabitSection(
                            rows = avoid,
                            onToggle = { _, _ -> },
                            onEdit = {},
                            onDelete = {},
                            onReorder = {},
                            onOpenTimed = {},
                            onVerifyHabit = {},
                            onScanTag = {},
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
}

private fun previewHabit(id: Long, name: String, type: HabitType, target: Int = 1, kind: HabitKind = HabitKind.GATING) =
    Habit(id = id, name = name, type = type, targetValue = target, kind = kind)

private fun previewProgress(habit: Habit, current: Int = 0, done: Boolean = false) =
    HabitProgress(habit = habit, currentValue = current, isCompleted = done)

internal val TWELVE_HABIT_ROWS: List<Pair<HabitProgress, HabitKind>> = listOf(
    previewProgress(previewHabit(1, "make bed", HabitType.PHOTO), done = true) to HabitKind.GATING,
    previewProgress(previewHabit(2, "walk pt 1", HabitType.STEPS, target = 1000)) to HabitKind.GATING,
    previewProgress(previewHabit(3, "meditate", HabitType.TIMED_MINUTES, target = 20), current = 3, done = true) to HabitKind.GATING,
    previewProgress(previewHabit(4, "write todos", HabitType.TALLY)) to HabitKind.TRACKED,
    previewProgress(previewHabit(5, "workout", HabitType.WORKOUT_MINUTES, target = 30)) to HabitKind.GATING,
    previewProgress(previewHabit(6, "drink water", HabitType.TALLY)) to HabitKind.TRACKED,
    previewProgress(previewHabit(7, "stretch", HabitType.TIMED_MINUTES, target = 10)) to HabitKind.GATING,
    previewProgress(previewHabit(8, "run", HabitType.WORKOUT_MINUTES, target = 20)) to HabitKind.GATING,
    previewProgress(previewHabit(9, "log lunch", HabitType.PHOTO)) to HabitKind.GATING,
    previewProgress(previewHabit(10, "walk pt 2", HabitType.STEPS, target = 4000)) to HabitKind.GATING,
    previewProgress(previewHabit(11, "no phone in bed", HabitType.TALLY, kind = HabitKind.ANTIHABIT)) to HabitKind.ANTIHABIT,
    previewProgress(previewHabit(12, "no snoozing", HabitType.TALLY, kind = HabitKind.ANTIHABIT)) to HabitKind.ANTIHABIT,
)

internal val FEW_HABIT_ROWS: List<Pair<HabitProgress, HabitKind>> = listOf(
    previewProgress(previewHabit(1, "make bed", HabitType.PHOTO), done = true) to HabitKind.GATING,
    previewProgress(previewHabit(2, "drink water", HabitType.TALLY)) to HabitKind.TRACKED,
    previewProgress(previewHabit(3, "no phone in bed", HabitType.TALLY, kind = HabitKind.ANTIHABIT)) to HabitKind.ANTIHABIT,
)

@Preview(name = "Today -- 12 habits", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TodayScreenPreviewTwelveHabits() {
    LockeTheme { TodayScreenPreviewScaffold(rows = TWELVE_HABIT_ROWS) }
}

@Preview(name = "Today -- a few habits", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TodayScreenPreviewFewHabits() {
    LockeTheme { TodayScreenPreviewScaffold(rows = FEW_HABIT_ROWS) }
}

@Preview(name = "Today -- empty", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TodayScreenPreviewEmpty() {
    LockeTheme { TodayScreenPreviewScaffold(rows = emptyList()) }
}
