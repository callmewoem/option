package com.habitsfirst.androidclone.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.service.AppUsageHabitSyncResult
import com.habitsfirst.androidclone.util.PermissionUtils
import java.text.DateFormat
import java.util.Date

/**
 * "Settings -> Diagnostics": a live, in-app look at exactly what the app-usage tracker
 * sees and does, for tracking down a "my habit's progress never updates" report without
 * needing a device connected to Logcat. Shows permission state, whether the background
 * worker is actually scheduled/running and what its last run did, every app-usage habit
 * that exists, and a button to run the sync right now with the full result (including
 * any crash) shown inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Permission") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Usage access", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (state.hasUsageAccess) "Granted" else "Not granted -- every habit below will read 0 minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.hasUsageAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        if (!state.hasUsageAccess) {
                            OutlinedButton(onClick = { context.startActivity(PermissionUtils.usageAccessSettingsIntent(context)) }) {
                                Text("Grant")
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Background worker") {
                    DiagnosticRow("Periodic (every ~15 min)", state.periodicWorkState)
                    DiagnosticRow("One-off (app open / leaving a tracked app)", state.oneOffWorkState)
                    DiagnosticRow("Last automatic run", state.lastAutoSyncAtEpochMillis?.let(::formatTimestamp) ?: "never")
                    DiagnosticRow("Habits it processed", state.lastAutoSyncHabitCount.toString())
                    state.lastAutoSyncError?.let { error ->
                        Spacer(modifier = Modifier.height(4.dp))
                        ErrorText(error)
                    }
                }
            }

            item {
                SectionCard(title = "\"Use an app\" habits (${state.appUsageHabits.size})") {
                    if (state.appUsageHabits.isEmpty()) {
                        Text("None exist yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.appUsageHabits.forEach { habit ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "target: ${habit.targetValue} min  ·  package: ${habit.targetPackageName ?: "(none set!)"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (habit.targetPackageName == null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::runManualSync,
                    enabled = !state.isRunningManualSync,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isRunningManualSync) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run sync now")
                }
            }

            state.manualSyncCrash?.let { crash ->
                item {
                    SectionCard(title = "Sync crashed", accentColor = MaterialTheme.colorScheme.error) {
                        ErrorText(crash)
                    }
                }
            }

            state.manualSyncReport?.let { report ->
                item {
                    SectionCard(title = "Last manual run result") {
                        DiagnosticRow("Ran at", formatTimestamp(report.ranAtEpochMillis))
                        DiagnosticRow("Usage access", if (report.hasUsageAccess) "granted" else "NOT granted")
                        DiagnosticRow("Candidates found", report.candidateCount.toString())
                        report.fatalError?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            ErrorText(it)
                        }
                        if (report.candidateCount == 0 && report.fatalError == null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "No habit matched the sync's own filter (type = \"use an app\" + active + has a target app). " +
                                    "If a habit is listed above but not here, that's the bug.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                items(report.results, key = { it.habitId }) { result ->
                    SyncResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    accentColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (accentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else accentColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun SyncResultCard(result: AppUsageHabitSyncResult) {
    val hitTarget = result.liveComputedMinutes >= result.targetMinutes
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.writeError != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (result.writeError != null) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (result.writeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(result.habitName, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("package: ${result.packageName}", style = MaterialTheme.typography.bodySmall)
            Text(
                "live from UsageStatsManager right now: ${result.liveComputedMinutes} min" +
                    (if (hitTarget) " (>= ${result.targetMinutes} target)" else " (target is ${result.targetMinutes})"),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "stored before this run: ${result.previousStoredMinutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.writeError?.let {
                Spacer(modifier = Modifier.height(4.dp))
                ErrorText("Failed to write progress: $it")
            }
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(epochMillis))
