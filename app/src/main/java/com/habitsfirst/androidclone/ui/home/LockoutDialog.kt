package com.habitsfirst.androidclone.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.data.repository.LockoutRepository
import com.habitsfirst.androidclone.ui.components.formatCountdown
import kotlinx.coroutines.delay

/**
 * Home's lockout sheet -- two entirely different screens depending on
 * [lockoutUntilEpochMillis], same split as the rest of the app's blocking toggles
 * (e.g. hard mode's own confirmation before turning off). Not active: pick a duration
 * and start it. Active: a live countdown, with ending it early gated behind a second
 * tap -- see [com.habitsfirst.androidclone.data.repository.LockoutRepository] for why
 * that friction lives here and nowhere else.
 */
@Composable
fun LockoutDialog(
    lockoutUntilEpochMillis: Long,
    onStart: (minutes: Int) -> Unit,
    onCancelLockout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isActive = lockoutUntilEpochMillis > System.currentTimeMillis()

    if (isActive) {
        ActiveLockoutContent(lockoutUntilEpochMillis, onCancelLockout, onDismiss)
    } else {
        StartLockoutContent(onStart, onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartLockoutContent(onStart: (minutes: Int) -> Unit, onDismiss: () -> Unit) {
    var selectedMinutes by remember { mutableIntStateOf(LockoutRepository.DURATION_PRESETS_MINUTES[1]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock everything") },
        text = {
            Column {
                Text(
                    text = "Every app on your block list stays locked for as long as you pick. " +
                        "Habits and grace tokens won't unlock it early.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                DurationPresetRow(
                    minutes = LockoutRepository.DURATION_PRESETS_MINUTES.take(3),
                    selectedMinutes = selectedMinutes,
                    onSelected = { selectedMinutes = it },
                )
                Spacer(modifier = Modifier.height(8.dp))
                DurationPresetRow(
                    minutes = LockoutRepository.DURATION_PRESETS_MINUTES.drop(3),
                    selectedMinutes = selectedMinutes,
                    onSelected = { selectedMinutes = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(selectedMinutes) }) { Text("Start lockout") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPresetRow(minutes: List<Int>, selectedMinutes: Int, onSelected: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        minutes.forEach { preset ->
            FilterChip(
                selected = preset == selectedMinutes,
                onClick = { onSelected(preset) },
                label = { Text(formatDurationLabel(preset)) },
            )
        }
    }
}

@Composable
private fun ActiveLockoutContent(
    lockoutUntilEpochMillis: Long,
    onCancelLockout: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmingCancel by remember { mutableStateOf(false) }
    var remainingSeconds by remember(lockoutUntilEpochMillis) {
        mutableIntStateOf(secondsUntil(lockoutUntilEpochMillis))
    }
    LaunchedEffect(lockoutUntilEpochMillis) {
        while (true) {
            remainingSeconds = secondsUntil(lockoutUntilEpochMillis)
            if (remainingSeconds <= 0) {
                // Ran out while this sheet was open -- nothing left to confirm or show.
                onDismiss()
                break
            }
            delay(1_000)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lockout active") },
        text = {
            Column {
                Text(
                    text = "Every app you've chosen to block stays locked for ${formatCountdown(remainingSeconds)} more.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (confirmingCancel) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You started this yourself. Habits and grace tokens can't end it early. Still want to?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (confirmingCancel) {
                TextButton(onClick = onCancelLockout) { Text("End lockout") }
            } else {
                TextButton(onClick = onDismiss) { Text("Keep it locked") }
            }
        },
        dismissButton = {
            if (confirmingCancel) {
                TextButton(onClick = { confirmingCancel = false }) { Text(stringResource(R.string.cancel)) }
            } else {
                TextButton(onClick = { confirmingCancel = true }) { Text("End early") }
            }
        },
    )
}

/** "15m", "1h", "4h", ... */
private fun formatDurationLabel(minutes: Int): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h"

/** Seconds from now until [targetEpochMillis]; 0 once past. */
private fun secondsUntil(targetEpochMillis: Long): Int =
    ((targetEpochMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L).toInt()
