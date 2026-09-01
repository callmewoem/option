package com.habitsfirst.androidclone.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.domain.model.HabitProgress

/**
 * Quick manual entry for Health-Connect-backed habits (steps, workout minutes, sleep
 * hours) that aren't auto-synced or don't have permission granted yet.
 * [com.habitsfirst.androidclone.domain.model.HabitType.APP_USAGE_MINUTES] tracks
 * automatically with no manual correction, and
 * [com.habitsfirst.androidclone.domain.model.HabitType.TIMED_MINUTES] habits use the
 * timer screen instead.
 */
@Composable
fun LogProgressDialog(
    progress: HabitProgress,
    onDismiss: () -> Unit,
    onConfirm: (newValue: Int) -> Unit,
) {
    var text by remember(progress.habit.id) { mutableStateOf(progress.currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(progress.habit.name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> if (input.all(Char::isDigit)) text = input },
                label = { Text("Current ${progress.habit.type.unit} (target ${progress.habit.targetValue})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull() ?: 0) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
