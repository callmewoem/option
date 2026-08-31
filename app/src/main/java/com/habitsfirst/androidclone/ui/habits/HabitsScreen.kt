package com.habitsfirst.androidclone.ui.habits

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure stats: the heatmap, completion rate by habit, and by day of week. Doing a habit
 * lives on Home; managing the list lives in Settings -- there's nothing to tap here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    onOpenSettings: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Stats") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                                HabitRateRow(
                                    name = stat.habit.name,
                                    rate = stat.rate,
                                    accent = stat.habit.kind.accentColor(),
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
private fun HabitRateRow(name: String, rate: Float, accent: Color) {
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
