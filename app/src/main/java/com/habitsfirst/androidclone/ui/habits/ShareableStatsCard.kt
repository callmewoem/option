package com.habitsfirst.androidclone.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Plain-data snapshot [ShareableStatsCard] renders -- deliberately narrower than
 * [HabitsUiState], so this stays capturable without a ViewModel and without pulling in
 * whatever the selected stats range happens to be. [weeklyLongestStreak] and
 * [weeklyPerfectDays] are computed the same way [HabitsViewModel.refreshStats] derives
 * `longestStreakInRange`/`perfectDaysInRange`, just windowed to the last 7 days instead
 * of the screen's selectable range -- "this week" is a fixed, shareable frame regardless
 * of whatever chip is selected on the Stats screen.
 */
data class ShareCardStats(
    val date: LocalDate,
    val currentStreak: Int,
    val todayCompletionFraction: Float,
    val weeklyPerfectDays: Int,
    val weeklyLongestStreak: Int,
)

/**
 * A self-contained "today's stats" card meant to look good both inline and as an
 * exported PNG (see [com.habitsfirst.androidclone.util.ComposeCaptureUtil]) -- generous
 * padding, the app's warm concrete/primary surface pairing, and monospace numeric
 * readouts, matching [StatCard] and the Home streak counter's visual language. Takes
 * plain [stats] rather than a ViewModel so it can be rendered off-screen for capture
 * without wiring up Hilt or a real screen lifecycle.
 */
@Composable
fun ShareableStatsCard(stats: ShareCardStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Locke",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stats.date.format(CARD_DATE_FORMAT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "${(stats.todayCompletionFraction * 100).roundToInt()}% done today",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShareStatTile(
                    label = "Streak",
                    value = "${stats.currentStreak}",
                    modifier = Modifier.weight(1f),
                )
                ShareStatTile(
                    label = "Perfect days (wk)",
                    value = "${stats.weeklyPerfectDays}",
                    modifier = Modifier.weight(1f),
                )
                ShareStatTile(
                    label = "Longest (wk)",
                    value = "${stats.weeklyLongestStreak}",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ShareStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val CARD_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
