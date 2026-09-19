package com.locke.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.locke.app.R
import com.locke.app.domain.model.HabitKind
import com.locke.app.domain.model.HabitProgress
import com.locke.app.domain.model.HabitType
import com.locke.app.ui.theme.LockeColor
import com.locke.app.ui.theme.LockePillShape
import com.locke.app.ui.theme.SpaceGrotesk
import com.locke.app.ui.theme.frauncesNumeral
import com.locke.app.ui.theme.spaceMono
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Which drawable icons a habit-type shows in its row -- design spec: "standard = quiet dot ... exercise = dumbbell, steps = footprints, timed = stopwatch, photo = camera." Types outside that five-way split fall back to the closest match. */
fun HabitType.todayIconRes(): Int = when (this) {
    HabitType.PHOTO -> R.drawable.ic_habit_camera
    HabitType.WORKOUT_MINUTES -> R.drawable.ic_habit_exercise
    HabitType.STEPS -> R.drawable.ic_habit_steps
    HabitType.TIMED_MINUTES, HabitType.APP_USAGE_MINUTES, HabitType.SLEEP_HOURS, HabitType.WAKATIME_CODING_MINUTES -> R.drawable.ic_habit_timed
    HabitType.TALLY, HabitType.VISIT_LOCATION, HabitType.GITHUB_CONTRIBUTION, HabitType.TAG_SCAN -> R.drawable.ic_habit_dot
}

/** A 40dp round button, 2px moss border -- design spec: "filled = primary action (+), outline = secondary (settings, lock)." */
@Composable
fun RoundIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(LockeColor.Moss) else Modifier)
            .border(2.dp, LockeColor.Moss, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (filled) LockeColor.BoneIn else LockeColor.Moss,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** A stat tile -- design spec: "50px wide, radius 4, filled moss = streak, outline = streak freezes." */
@Composable
fun StatTile(
    value: String,
    iconRes: Int,
    iconWidth: androidx.compose.ui.unit.Dp,
    iconHeight: androidx.compose.ui.unit.Dp,
    filled: Boolean,
    contentDescriptionText: String,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (filled) LockeColor.BoneIn else LockeColor.HabitIconDefault
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .then(if (filled) Modifier.background(LockeColor.Moss) else Modifier)
            .border(1.5.dp, if (filled) LockeColor.Moss else LockeColor.MossMid, RoundedCornerShape(4.dp))
            .semantics { contentDescription = contentDescriptionText },
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(width = iconWidth, height = iconHeight),
        )
        Text(
            text = value,
            style = androidx.compose.ui.text.TextStyle(fontFamily = SpaceGrotesk, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp),
            color = contentColor,
        )
    }
}

/**
 * Today's hero: the big habits-left numeral, the 7x3 today heatmap, and the
 * streak/streak-freeze tiles above the "HABITS LEFT" label (design spec §2, "Today
 * screen anatomy"). [dayScores] is the same 0f..1f-per-date map [Heatmap] uses
 * elsewhere; [todayFraction] overrides today's own cell so it fills live as habits are
 * completed rather than waiting for a saved day score.
 *
 * Streak freezes have no data source yet anywhere in the app (no such concept exists in
 * the domain model or repository) -- [freezeCount] is a stub, always 0, until that
 * feature exists.
 */
@Composable
fun TodayHero(
    habitsLeft: Int,
    streakDays: Int,
    dayScores: Map<java.time.LocalDate, Float>,
    todayFraction: Float,
    modifier: Modifier = Modifier,
    freezeCount: Int = 0, // stub -- no streak-freeze data source yet
) {
    val today = remember { java.time.LocalDate.now() }
    val rangeStart = remember(today) { today.minusWeeks(2) }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$habitsLeft",
            style = frauncesNumeral(),
            color = LockeColor.MossDark,
            maxLines = 1,
            modifier = Modifier.width(122.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "HABITS LEFT",
                style = spaceMono(11.sp, letterSpacing = 2.6.sp),
                color = LockeColor.LabelOlive,
            )
            Spacer(modifier = Modifier.height(12.dp))
            val rows = 3
            val statTileColumnWidth = 50.dp
            val statTileGap = 10.dp
            // BoxWithConstraints up front to resolve the heatmap's cell size (and from
            // it, the grid's exact pixel height) before laying out either sibling --
            // BoxWithConstraints is built on SubcomposeLayout, which can't itself be
            // intrinsically measured, so an IntrinsicSize.Min row (the more obvious
            // "stretch to the tallest sibling" approach) isn't an option here. Handing
            // both children the same already-known height directly is the same result
            // CSS flexbox's default align-items: stretch gets for free in the HTML
            // reference, without needing intrinsics at all.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gap = 4.dp
                val cellSize = ((maxWidth - statTileGap - statTileColumnWidth - gap * 6) / 7).coerceAtLeast(8.dp)
                val heatmapHeight = cellSize * rows + gap * (rows - 1)
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.weight(1f).height(heatmapHeight)) {
                        Heatmap(
                            startDate = rangeStart,
                            endDate = today,
                            cellSize = cellSize,
                            cellGap = gap,
                            cellShape = HeatmapCellShape.Square,
                            cellCornerRadius = 3.dp,
                            weeksAsRows = true,
                            scrollable = false,
                            fixedRowCount = rows,
                            colorForDate = { date ->
                                val fraction = if (date == today) todayFraction else (dayScores[date] ?: 0f)
                                todayHeatmapLevelColor(fraction)
                            },
                            borderColorForDate = { date ->
                                val fraction = if (date == today) todayFraction else (dayScores[date] ?: 0f)
                                if (fraction <= 0f) LockeColor.HeatmapEmptyOutline else todayHeatmapLevelColor(fraction)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.width(statTileGap))
                    Column(
                        modifier = Modifier.width(statTileColumnWidth).height(heatmapHeight),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatTile(
                            value = "$streakDays",
                            iconRes = R.drawable.ic_flame,
                            iconWidth = 12.dp,
                            iconHeight = 14.dp,
                            filled = true,
                            contentDescriptionText = "$streakDays day streak",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        StatTile(
                            value = "$freezeCount",
                            iconRes = R.drawable.ic_shield,
                            iconWidth = 12.dp,
                            iconHeight = 13.dp,
                            filled = false,
                            contentDescriptionText = "$freezeCount streak freezes left",
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Heatmap levels per the design spec: 0 = transparent, 1 = light moss, 2 = mid moss, 3 = dark moss. */
private fun todayHeatmapLevelColor(fraction: Float): Color = when {
    fraction <= 0f -> Color.Transparent
    fraction < 0.5f -> LockeColor.MossLight
    fraction < 1f -> LockeColor.MossMid
    else -> LockeColor.Moss
}

/** The `done/total` fraction, progress bar, lockout button and add button (design spec §3). */
@Composable
fun TodayProgressRow(
    completed: Int,
    total: Int,
    onLockout: () -> Unit,
    onAddHabit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else 0f
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(
            text = "$completed/$total",
            style = spaceMono(11.sp, weight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.2.sp),
            color = LockeColor.LabelOlive,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(LockeColor.EmptyTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(LockeColor.Moss, RoundedCornerShape(4.dp)),
            )
        }
        RoundIconButton(
            iconRes = R.drawable.ic_lock_round,
            contentDescription = "Temporary lockout",
            onClick = onLockout,
            iconSize = 16.dp,
        )
        RoundIconButton(
            iconRes = R.drawable.ic_plus_round,
            contentDescription = "Add habit",
            onClick = onAddHabit,
            filled = true,
            iconSize = 16.dp,
        )
    }
}

/**
 * One habit row (design spec §3/§6): a 44dp pill, tap the leading circle to toggle
 * complete, swipe left to reveal Edit/Delete (112dp), long-press (~450ms) then drag to
 * reorder in 50dp steps. The circle's own tap is a nested child composable, so a
 * horizontal or vertical drag starting there is still picked up by this row's own
 * gesture detectors once it crosses touch slop -- Compose resolves the plain-tap case
 * (child consumes first) and the drag case (parent's slop-gated detectors take over)
 * without the two ever double-firing.
 */
@Composable
fun HabitPill(
    title: String,
    subtitle: String?,
    iconRes: Int,
    iconTint: Color,
    isCompleted: Boolean,
    isAvoid: Boolean,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isBeingDragged: Boolean,
    dragOffsetPx: Float,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val revealWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 112.dp.toPx() }
    val swipeOffset = remember { Animatable(0f) }

    val pillShape = RoundedCornerShape(22.dp)
    val innerShape = RoundedCornerShape(21.dp)
    val surfaceColor = if (isCompleted) LockeColor.SurfaceCompleted else LockeColor.BoneIn
    val borderColor = if (isCompleted) LockeColor.SurfaceCompletedBorder else LockeColor.HabitBorder

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer {
                translationY = if (isBeingDragged) dragOffsetPx else 0f
                scaleX = if (isBeingDragged) 1.03f else 1f
                scaleY = if (isBeingDragged) 1.03f else 1f
                shadowElevation = if (isBeingDragged) 10f else 0f
            }
            .zIndex(if (isBeingDragged) 1f else 0f)
            .clip(pillShape)
            .border(BorderStroke(1.dp, borderColor), pillShape)
            .background(surfaceColor, pillShape),
    ) {
        // Swipe-reveal Edit/Delete, underneath the sliding foreground row.
        Row(modifier = Modifier.align(Alignment.CenterEnd).width(112.dp).fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(LockeColor.EditFill)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Edit",
                    style = androidx.compose.ui.text.TextStyle(fontFamily = SpaceGrotesk, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 12.sp),
                    color = LockeColor.Moss,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(LockeColor.DeleteFill)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(LockeColor.HabitBorder),
                )
                Text(
                    "Delete",
                    style = androidx.compose.ui.text.TextStyle(fontFamily = SpaceGrotesk, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 12.sp),
                    color = LockeColor.DeleteText,
                )
            }
        }

        // Foreground row -- slides left on swipe, lifts and tracks the finger on reorder-drag.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                .clip(innerShape)
                .background(surfaceColor, innerShape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (swipeOffset.value < -revealWidthPx / 2f) -revealWidthPx else 0f
                                swipeOffset.animateTo(target, tween(200))
                            }
                        },
                        onDragCancel = { scope.launch { swipeOffset.animateTo(0f, tween(200)) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(-revealWidthPx, 0f))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    ) { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.y)
                    }
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggleComplete,
                    )
                    .semantics { contentDescription = "Mark as done" },
                contentAlignment = Alignment.Center,
            ) {
                val circleFill = if (isCompleted) (if (isAvoid) LockeColor.Avoid else LockeColor.Moss) else Color.Transparent
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(circleFill)
                        .border(2.dp, if (isCompleted) Color.Transparent else LockeColor.MutedTextFaint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_mark),
                            contentDescription = null,
                            tint = LockeColor.BoneIn,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = title,
                style = androidx.compose.ui.text.TextStyle(fontFamily = SpaceGrotesk, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = (-0.1).sp),
                color = LockeColor.Ink,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = spaceMono(10.5.sp, letterSpacing = (-0.3).sp),
                    color = LockeColor.MutedTextData,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The mono progress trailing a measurable habit's row, e.g. "0 / 1000 steps" -- null for non-measurable types, which show no trailing stat. */
fun habitPillSubtitle(progress: HabitProgress): String? {
    val habit = progress.habit
    if (!habit.type.isMeasurable) return null
    return "${progress.currentValue} / ${habit.targetValue} ${habit.type.unit}".trim()
}

/** The row's leading icon tint -- moss-mid once done, the design spec's quiet default otherwise, always terracotta for an avoid habit's no-entry icon. */
fun habitPillIconTint(kind: HabitKind, isCompleted: Boolean): Color = when {
    kind == HabitKind.ANTIHABIT -> LockeColor.Avoid
    isCompleted -> LockeColor.MossMid
    else -> LockeColor.HabitIconDefault
}
