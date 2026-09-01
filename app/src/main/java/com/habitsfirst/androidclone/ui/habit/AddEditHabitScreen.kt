package com.habitsfirst.androidclone.ui.habit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.toScheduleLabel
import com.habitsfirst.androidclone.ui.components.icon
import com.habitsfirst.androidclone.ui.components.label
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHabitScreen(
    onDone: () -> Unit,
    onOpenMeditationTimer: (Long) -> Unit,
    viewModel: AddEditHabitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAppPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.add_habit_title else R.string.edit_habit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.canDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.add_habit_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text(stringResource(R.string.add_habit_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "Kind", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HabitKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { viewModel.onKindChanged(kind) },
                        enabled = !state.isKindLocked,
                        label = { Text(kind.label) },
                    )
                }
            }
            Text(
                text = when {
                    state.isKindLocked -> "Locked by Hard Mode"
                    state.kind == HabitKind.GATING -> "Gates your locked apps."
                    state.kind == HabitKind.TRACKED -> "Tracked only, never blocks."
                    else -> "Log only when you slip."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = stringResource(R.string.add_habit_choose_type), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HabitType.entries.take(3).forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChanged(type) },
                        label = { Text(type.label(), maxLines = 1) },
                        leadingIcon = { Icon(type.icon(), contentDescription = null) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HabitType.entries.drop(3).forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChanged(type) },
                        label = { Text(type.label(), maxLines = 1) },
                        leadingIcon = { Icon(type.icon(), contentDescription = null) },
                    )
                }
            }

            if (state.type.isMeasurable) {
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = state.targetValue.toString(),
                    onValueChange = { text -> text.toIntOrNull()?.let(viewModel::onTargetValueChanged) },
                    label = {
                        Text(
                            stringResource(
                                if (state.type == HabitType.STEPS) {
                                    R.string.add_habit_target_steps
                                } else {
                                    R.string.add_habit_target_minutes
                                },
                            ),
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            if (state.type == HabitType.APP_USAGE_MINUTES) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.add_habit_choose_app), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { showAppPicker = true },
                    label = { Text(state.targetAppLabel ?: "Choose app") },
                )
            }

            if (state.type == HabitType.CUSTOM) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onRequiresPhotoVerificationToggled(!state.requiresPhotoVerification) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Require a photo", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "A vision model checks a submitted proof photo before this counts as done.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.requiresPhotoVerification,
                        onCheckedChange = viewModel::onRequiresPhotoVerificationToggled,
                    )
                }

                if (state.requiresPhotoVerification) {
                    Spacer(modifier = Modifier.height(16.dp))
                    VerificationSetupSection(
                        prompt = state.verificationPrompt,
                        onPromptChanged = viewModel::onVerificationPromptChanged,
                        exampleImagePath = state.verificationExampleImagePath,
                        onExampleImagePicked = viewModel::onExampleImageSelected,
                        onExampleImageCleared = viewModel::onExampleImageCleared,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "Frequency", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.scheduledDays.toScheduleLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FrequencyPicker(
                scheduledDays = state.scheduledDays,
                onEveryDaySelected = viewModel::onEveryDaySelected,
                onDayToggled = viewModel::onScheduledDayToggled,
            )

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = viewModel::onSave,
                enabled = state.isValid && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_habit_save))
            }

            if (!state.isNew && state.type == HabitType.MEDITATION_MINUTES) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onOpenMeditationTimer(state.habitId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open meditation timer")
                }
            }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(stringResource(R.string.add_habit_choose_app)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.installedApps, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onTargetAppSelected(app)
                                    showAppPicker = false
                                },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete habit?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.onDelete() }) {
                    Text(stringResource(R.string.add_habit_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * Lets the user pick which days of the week a habit is due on -- "Every day" (the
 * default) or specific days, e.g. "hoover" every Sunday. Tapping any day chip narrows
 * [scheduledDays] to just the selected days; tapping "Every day" clears it back to
 * empty, which [com.habitsfirst.androidclone.domain.model.Habit.isDaily] reads as due every day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyPicker(
    scheduledDays: Set<DayOfWeek>,
    onEveryDaySelected: () -> Unit,
    onDayToggled: (DayOfWeek) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = scheduledDays.isEmpty(),
            onClick = onEveryDaySelected,
            label = { Text("Every day") },
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DayOfWeek.values().forEach { day ->
            FilterChip(
                selected = day in scheduledDays,
                onClick = { onDayToggled(day) },
                label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
            )
        }
    }
}

/** Setup for a [HabitType.CUSTOM] habit with photo verification on: a description, an example photo, or both. */
@Composable
private fun VerificationSetupSection(
    prompt: String,
    onPromptChanged: (String) -> Unit,
    exampleImagePath: String?,
    onExampleImagePicked: (Uri) -> Unit,
    onExampleImageCleared: () -> Unit,
) {
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onExampleImagePicked) }

    Text(text = "How should we verify it?", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Describe what a proof photo should show, add an example photo, or both. " +
            "A vision model checks each submitted photo against this before the habit counts as done.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChanged,
        label = { Text("What counts as done? (optional)") },
        placeholder = { Text("e.g. \"A made bed with pillows arranged\"") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    Spacer(modifier = Modifier.height(12.dp))

    if (exampleImagePath != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsyncImage(
                model = exampleImagePath,
                contentDescription = "Example photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            IconButton(onClick = onExampleImageCleared) {
                Icon(Icons.Filled.Close, contentDescription = "Remove example photo")
            }
        }
    } else {
        OutlinedButton(
            onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add example photo (optional)")
        }
    }
}
