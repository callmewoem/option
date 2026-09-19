package com.locke.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val CellSize = 12.dp
private val CellGap = 3.dp

/** Which primitive [Heatmap] draws per cell -- dots for the Stats screen's contribution grid, squares for the Today hero's compact strip (design spec: "square, radius 3"). */
enum class HeatmapCellShape { Circle, Square }

/**
 * A contribution grid, colored by [colorForDate] -- purely a renderer, callers decide
 * what a date's color means (continuous completion-fraction shading for the aggregate
 * heatmap, or a flat done/slipped/empty color for a single habit's strip).
 *
 * By default one column per week and one row per day-of-week (Sunday at top), matching
 * the Stats screen's contribution grid. [weeksAsRows] transposes that -- one row per
 * week, one column per day-of-week -- for the Today hero's compact 7-wide grid (design
 * spec: "7 columns (days) x 3 rows (weeks)").
 */
@Composable
fun Heatmap(
    startDate: LocalDate,
    endDate: LocalDate,
    colorForDate: (LocalDate) -> Color,
    modifier: Modifier = Modifier,
    cellSize: Dp = CellSize,
    cellGap: Dp = CellGap,
    cellShape: HeatmapCellShape = HeatmapCellShape.Circle,
    cellCornerRadius: Dp = 3.dp,
    weeksAsRows: Boolean = false,
    /** Square cells only -- an outline drawn regardless of fill, e.g. the design spec's empty-cell border. Null draws no outline. */
    borderColorForDate: ((LocalDate) -> Color)? = null,
    scrollable: Boolean = true,
) {
    val gridStart = startDate.minusDays(((startDate.dayOfWeek.value % 7).toLong())) // back up to Sunday
    val totalDays = ChronoUnit.DAYS.between(gridStart, endDate).toInt() + 1
    val weekCount = (totalDays + 6) / 7

    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val gapPx = with(density) { cellGap.toPx() }
    val cornerPx = with(density) { cellCornerRadius.toPx() }
    val width: Dp = (cellSize + cellGap) * (if (weeksAsRows) 7 else weekCount)
    val height: Dp = (cellSize + cellGap) * (if (weeksAsRows) weekCount else 7)

    val canvas = @Composable {
        Canvas(modifier = Modifier.width(width).height(height)) {
            for (week in 0 until weekCount) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    val inRange = date in startDate..endDate
                    val color = if (inRange) colorForDate(date) else Color.Transparent
                    val x = if (weeksAsRows) dow * (cellPx + gapPx) else week * (cellPx + gapPx)
                    val y = if (weeksAsRows) week * (cellPx + gapPx) else dow * (cellPx + gapPx)
                    when (cellShape) {
                        HeatmapCellShape.Circle -> drawCircle(
                            color = color,
                            radius = cellPx / 2f,
                            center = Offset(x + cellPx / 2f, y + cellPx / 2f),
                        )
                        HeatmapCellShape.Square -> {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(x, y),
                                size = Size(cellPx, cellPx),
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                            )
                            val borderColor = borderColorForDate?.invoke(date)
                            if (inRange && borderColor != null) {
                                drawRoundRect(
                                    color = borderColor,
                                    topLeft = Offset(x, y),
                                    size = Size(cellPx, cellPx),
                                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                                    style = Stroke(width = with(density) { 1.5.dp.toPx() }),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (scrollable) {
        Box(modifier = modifier.horizontalScroll(rememberScrollState())) { canvas() }
    } else {
        Box(modifier = modifier) { canvas() }
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
