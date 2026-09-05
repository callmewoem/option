package com.habitsfirst.androidclone.ui.habits

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.ui.components.Heatmap
import com.habitsfirst.androidclone.ui.components.accentColor
import com.habitsfirst.androidclone.ui.components.heatmapFractionColor
import com.habitsfirst.androidclone.ui.navigation.LockeBottomBar
import com.habitsfirst.androidclone.util.ComposeCaptureUtil
import com.habitsfirst.androidclone.util.captureGraphicsLayer
import com.habitsfirst.androidclone.util.rememberCaptureGraphicsLayer
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Pure stats: a selectable-range streak summary, the heatmap, completion rate by
 * habit, and by day of week. Doing a habit lives on Home; managing the list lives in
 * Settings -- there's nothing to tap here besides the range chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    onOpenSettings: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val shareCardLayer = rememberCaptureGraphicsLayer()
    var shareCardStats by remember { mutableStateOf<ShareCardStats?>(null) }
    // Guards against a double-tap launching two overlapping captures against the same
    // shareCardLayer/shareCardStats -- without this, the second tap's state write could
    // land mid-capture and the first share would go out carrying the second tap's numbers.
    var isSharingStats by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Stats") },
                actions = {
                    IconButton(
                        enabled = !isSharingStats,
                        onClick = {
                            isSharingStats = true
                            coroutineScope.launch {
                                try {
                                    // Push fresh numbers into the off-screen card below, then give
                                    // Compose two frames to recompose/layout/draw it before reading
                                    // the graphics layer back -- toImageBitmap() only sees whatever
                                    // was last actually drawn.
                                    shareCardStats = viewModel.loadShareCardStats()
                                    withFrameNanos {}
                                    withFrameNanos {}
                                    val file = ComposeCaptureUtil.captureToPng(context, shareCardLayer)
                                    if (file != null) {
                                        shareStatsCard(context, file)
                                    }
                                } finally {
                                    isSharingStats = false
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share today's stats")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(),
            )
        },
        bottomBar = { LockeBottomBar(navController) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableRanges.forEach { range ->
                            FilterChip(
                                selected = state.range == range,
                                onClick = { viewModel.onRangeSelected(range) },
                                label = { Text(range.label) },
                            )
                        }
                    }
                }

                item {
                    StreakSummaryRow(
                        currentStreak = state.currentStreak,
                        longestStreakInRange = state.longestStreakInRange,
                        perfectDaysInRange = state.perfectDaysInRange,
                        brokenStreaksInRange = state.scarredDates.size,
                    )
                }

                item {
                    val today = LocalDate.now()
                    // Canvas draws in a DrawScope, not a composable context, so these have
                    // to be resolved here and captured by value, not read inside colorForDate.
                    val primary = MaterialTheme.colorScheme.primary
                    val secondary = MaterialTheme.colorScheme.secondary
                    val error = MaterialTheme.colorScheme.error
                    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
                    Heatmap(
                        startDate = today.minusWeeks(state.range.weeks),
                        endDate = today,
                        colorForDate = { date ->
                            val score = state.dayScores[date]
                            when {
                                date in state.goldStarDates -> secondary
                                date in state.scarredDates -> error
                                score == null -> emptyColor
                                else -> heatmapFractionColor(score, primary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Column {
                        Text("Completion by habit", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        KindLegend()
                        Spacer(modifier = Modifier.height(12.dp))
                        if (state.completionStats.isEmpty()) {
                            Text(
                                "No data yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.completionStats
                                .sortedWith(compareBy({ it.habit.kind.ordinal }, { -it.rate }))
                                .forEach { stat ->
                                    // For an ANTIHABIT, completedCount is the raw slip count -- invert it to
                                    // the clean-day count so the fraction shown matches the (already-inverted) bar.
                                    val doneCount = if (stat.habit.kind == HabitKind.ANTIHABIT) {
                                        stat.totalDays - stat.completedCount
                                    } else {
                                        stat.completedCount
                                    }
                                    HabitRateRow(
                                        name = stat.habit.name,
                                        rate = stat.rate,
                                        accent = stat.habit.kind.accentColor(),
                                        countLabel = "$doneCount/${stat.totalDays}",
                                    )
                                }
                        }
                    }
                }

                item {
                    Column {
                        Text("By day of week", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        DayOfWeekChart(stats = state.dayOfWeekStats, accent = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Off-screen host for the shareable stats card -- always composed (so the
            // graphics layer below has *something* drawn to read back the very first
            // time Share is tapped), positioned far outside the viewport so it's never
            // actually visible. Compose doesn't clip a child to its parent's bounds by
            // default, so this still measures/lays out/draws normally; it just never
            // lands inside the screen's visible area.
            Box(modifier = Modifier.offset(x = 4000.dp)) {
                ShareableStatsCard(
                    stats = shareCardStats ?: ShareCardStats(
                        date = LocalDate.now(),
                        currentStreak = 0,
                        todayCompletionFraction = 0f,
                        weeklyPerfectDays = 0,
                        weeklyLongestStreak = 0,
                    ),
                    modifier = Modifier
                        .width(360.dp)
                        .captureGraphicsLayer(shareCardLayer),
                )
            }
        }
    }
}

/** Hands [file] (an already-saved PNG) to the system share sheet as an `image/png` stream. */
private fun shareStatsCard(context: Context, file: File) {
    val uri = ComposeCaptureUtil.uriFor(context, file)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share today's stats"))
}

/** Four at-a-glance streak figures for the selected range -- current streak isn't windowed, the other three are. */
@Composable
private fun StreakSummaryRow(
    currentStreak: Int,
    longestStreakInRange: Int,
    perfectDaysInRange: Int,
    brokenStreaksInRange: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(label = "Streak", value = "$currentStreak", modifier = Modifier.weight(1f))
        StatCard(label = "Longest", value = "$longestStreakInRange", modifier = Modifier.weight(1f))
        StatCard(label = "Perfect days", value = "$perfectDaysInRange", modifier = Modifier.weight(1f))
        StatCard(
            label = "Broken",
            value = "$brokenStreaksInRange",
            valueColor = if (brokenStreaksInRange > 0) MaterialTheme.colorScheme.error else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KindLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HabitKind.entries.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).background(kind.accentColor(), CircleShape))
                Text(kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HabitRateRow(name: String, rate: Float, accent: Color, countLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(rate.coerceIn(0f, 1f))
                    .background(accent),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = countLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${(rate * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun DayOfWeekChart(stats: List<DayOfWeekStat>, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stats.forEach { stat ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.height(64.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height((4 + 60 * stat.averageFraction.coerceIn(0f, 1f)).dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(accent),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stat.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
