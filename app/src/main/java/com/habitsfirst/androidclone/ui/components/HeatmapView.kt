package com.habitsfirst.androidclone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val CellSize = 12.dp
private val CellGap = 3.dp

/**
 * A dot-grid contribution grid: one column per week, one row per day-of-week (Sunday
 * at top), colored by [colorForDate]. Purely a renderer -- callers decide what a date's
 * color means (continuous completion-fraction shading for the aggregate heatmap, or a
 * flat done/slipped/empty color for a single habit's strip).
 */
@Composable
fun Heatmap(
    startDate: LocalDate,
    endDate: LocalDate,
    colorForDate: (LocalDate) -> Color,
    modifier: Modifier = Modifier,
) {
    val gridStart = startDate.minusDays(((startDate.dayOfWeek.value % 7).toLong())) // back up to Sunday
    val totalDays = ChronoUnit.DAYS.between(gridStart, endDate).toInt() + 1
    val weekCount = (totalDays + 6) / 7

    val density = LocalDensity.current
    val cellPx = with(density) { CellSize.toPx() }
    val gapPx = with(density) { CellGap.toPx() }
    val width: Dp = (CellSize + CellGap) * weekCount
    val height: Dp = (CellSize + CellGap) * 7

    Box(modifier = modifier.horizontalScroll(rememberScrollState())) {
        Canvas(modifier = Modifier.width(width).height(height)) {
            for (week in 0 until weekCount) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    val color = if (date in startDate..endDate) colorForDate(date) else Color.Transparent
                    drawCircle(
                        color = color,
                        radius = cellPx / 2f,
                        center = Offset(
                            week * (cellPx + gapPx) + cellPx / 2f,
                            dow * (cellPx + gapPx) + cellPx / 2f,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Standard 5-bucket GitHub-style shading of [baseColor] by a 0f..1f fraction. Plain
 * (non-@Composable) on purpose: [Heatmap]'s `colorForDate` runs inside a Canvas
 * DrawScope, not a composable context, so the caller must resolve
 * `MaterialTheme.colorScheme.primary` once beforehand and pass it in.
 */
fun heatmapFractionColor(fraction: Float, baseColor: Color): Color {
    val alpha = when {
        fraction <= 0f -> 0.18f
        fraction < 0.34f -> 0.4f
        fraction < 0.67f -> 0.62f
        fraction < 1f -> 0.82f
        else -> 1f
    }
    return baseColor.copy(alpha = alpha)
}

/** Day-of-week initials aligned with [Heatmap]'s Sunday-first rows, for a small legend. */
val HeatmapDayLabels: List<DayOfWeek> = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
)
