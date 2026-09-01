package com.habitsfirst.androidclone.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.BuildConfig
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.data.healthconnect.HealthConnectManager
import com.habitsfirst.androidclone.data.repository.ProofOfLifeRepository
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.ThemeVariant
import com.habitsfirst.androidclone.ui.components.icon
import com.habitsfirst.androidclone.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onManageApps: () -> Unit,
    onManageUrls: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSkipHabitDialog by remember { mutableStateOf(false) }
    var themeCodeInput by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val themeCodeMessage by viewModel.themeCodeMessage.collectAsStateWithLifecycle()
    LaunchedEffect(themeCodeMessage) {
        themeCodeMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onThemeCodeMessageShown()
        }
    }

    // Permission grants happen in system Settings, outside this screen -- re-read them
    // whenever the user comes back so the rows reflect reality.
    var permissionRefreshTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRefreshTick++ }
    val hasUsageAccess = remember(permissionRefreshTick) { PermissionUtils.hasUsageAccess(context) }
    val hasAccessibility = remember(permissionRefreshTick) { PermissionUtils.isAccessibilityServiceEnabled(context) }
    val hasOverlay = remember(permissionRefreshTick) { PermissionUtils.hasOverlayPermission(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshHealthConnectPermissions() }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onHealthConnectPermissionResult(granted.containsAll(HealthConnectManager.PERMISSIONS)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Both insets matter here: without the top one, the TopAppBar visually and
        // functionally covers the first section header/row underneath it -- with a
        // single habit, that's the only habit, leaving nothing tappable to edit or
        // delete it.
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { SectionHeader(stringResource(R.string.settings_habits)) }
            items(state.habits, key = { it.id }) { habit ->
                ListItem(
                    headlineContent = { Text(habit.name) },
                    supportingContent = {
                        val target = habit.displayTarget.ifBlank { "Custom check-in" }
                        val schedule = if (habit.isDaily) null else " · ${habit.scheduleLabel}"
                        Text("${habit.kind.label} · $target${schedule.orEmpty()}")
                    },
                    leadingContent = { Icon(habit.type.icon(), contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditHabit(habit.id) },
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_add_habit)) },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddHabit),
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_blocked_apps)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_manage_apps)) },
                    leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManageApps),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Blocked websites") },
                    supportingContent = { Text("Premade porn/social lists, plus your own custom lists") },
                    leadingContent = { Icon(Icons.Filled.Public, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManageUrls),
                )
                HorizontalDivider()
            }

            item { SectionHeader("Theme") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeVariant.entries.forEach { variant ->
                        val unlocked = variant in state.unlockedThemeVariants
                        FilterChip(
                            selected = state.selectedThemeVariant == variant,
                            onClick = { viewModel.onThemeVariantSelected(variant) },
                            enabled = unlocked,
                            // A fillMaxWidth Row with 4 chips can squeeze one below its
                            // natural width and wrap "Concrete"/"Ink" onto a second line;
                            // scrolling (above) avoids that squeeze, and this is the
                            // belt-and-suspenders fallback -- overflow with an ellipsis
                            // rather than ever wrapping to a second line.
                            label = { Text(variant.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = if (!unlocked) {
                                { Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                Text(
                    "Locked themes are won from the daily lootbox -- or unlocked instantly with a code below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = themeCodeInput,
                        onValueChange = { themeCodeInput = it },
                        label = { Text("Theme code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            viewModel.onRedeemThemeCode(themeCodeInput)
                            themeCodeInput = ""
                        },
                        enabled = themeCodeInput.isNotBlank(),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                        Text("Redeem")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            }

            item { SectionHeader("Rewards") }
            item {
                ListItem(
                    headlineContent = { Text("Grace tokens") },
                    supportingContent = { Text("1-minute unblock, redeemed from a lock screen") },
                    leadingContent = { Icon(Icons.Filled.Redeem, contentDescription = null) },
                    trailingContent = { Text("${state.graceTokenCount}", style = MaterialTheme.typography.titleMedium) },
                )
                ListItem(
                    headlineContent = { Text("Task-skip tokens") },
                    supportingContent = { Text("Force-completes one gating habit for today") },
                    leadingContent = { Icon(Icons.Filled.Redeem, contentDescription = null) },
                    trailingContent = { Text("${state.taskSkipTokenCount}", style = MaterialTheme.typography.titleMedium) },
                )
                if (state.taskSkipTokenCount > 0) {
                    OutlinedButton(
                        onClick = { showSkipHabitDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text("Skip a habit today")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }

            item { SectionHeader("Bedtime lock") }
            item {
                BedtimeAndReminderSection(
                    bedtimeEnabled = state.bedtimeEnabled,
                    bedtimeStart = state.bedtimeStart,
                    bedtimeEnd = state.bedtimeEnd,
                    onBedtimeChanged = viewModel::onBedtimeChanged,
                    morningReminderEnabled = state.morningReminderEnabled,
                    morningReminderTime = state.morningReminderTime,
                    onMorningReminderChanged = viewModel::onMorningReminderChanged,
                    proofOfLifeEnabled = state.proofOfLifeEnabled,
                    proofOfLifeTime = state.proofOfLifeTime,
                    proofOfLifeWindowMinutes = state.proofOfLifeWindowMinutes,
                    onProofOfLifeChanged = viewModel::onProofOfLifeChanged,
                )
            }

            item { SectionHeader("Hard mode") }
            item {
                val cooldownDaysLeft = daysUntil(state.hardModeToggleLockedUntilEpochMillis)
                val toggleLocked = cooldownDaysLeft > 0
                ListItem(
                    headlineContent = { Text("Hard mode") },
                    supportingContent = {
                        Text(
                            buildString {
                                append(
                                    if (state.hardModeEnabled) {
                                        "Gates and blocked apps can only be added, never removed."
                                    } else {
                                        "Locks in your gates and blocked apps. Grants 5 grace tokens."
                                    },
                                )
                                if (toggleLocked) {
                                    append(
                                        " Can't be toggled again for $cooldownDaysLeft more " +
                                            if (cooldownDaysLeft == 1) "day." else "days.",
                                    )
                                }
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = state.hardModeEnabled,
                            onCheckedChange = viewModel::onHardModeToggled,
                            enabled = !toggleLocked,
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }

            item { SectionHeader("Ease into it") }
            item {
                Text(
                    "How many consistent days before onboarding's next habit unlocks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(3, 5, 7).forEach { days ->
                        FilterChip(
                            selected = state.easeInStreakLength == days,
                            onClick = { viewModel.onEaseInStreakLengthChanged(days) },
                            label = { Text("$days days") },
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            }

            if (state.healthConnectAvailable) {
                item { SectionHeader("Health Connect") }
                item {
                    HealthConnectSection(
                        permissionsGranted = state.healthConnectPermissionsGranted,
                        syncEnabled = state.healthConnectSyncEnabled,
                        onRequestPermissions = { healthConnectPermissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
                        onSyncToggled = viewModel::onHealthConnectSyncToggled,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_permissions)) }
            item {
                PermissionRow(
                    title = stringResource(R.string.permission_usage_access_title),
                    granted = hasUsageAccess,
                    onClick = { context.startActivity(PermissionUtils.usageAccessSettingsIntent(context)) },
                )
            }
            item {
                PermissionRow(
                    title = stringResource(R.string.permission_accessibility_title),
                    granted = hasAccessibility,
                    onClick = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
                )
            }
            item {
                PermissionRow(
                    title = stringResource(R.string.permission_overlay_title),
                    granted = hasOverlay,
                    onClick = { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.permission_notifications_title)) },
                    trailingContent = {
                        Switch(
                            checked = state.notificationsEnabled,
                            onCheckedChange = viewModel::onNotificationsToggled,
                        )
                    },
                )
            }

            item { SectionHeader("Photo verification") }
            item {
                ApiKeyField(
                    apiKey = state.anthropicApiKey,
                    onApiKeyChanged = viewModel::onAnthropicApiKeyChanged,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                )
            }
        }
    }

    if (showSkipHabitDialog) {
        val skippable = state.habits.filter { it.kind == HabitKind.GATING }
        AlertDialog(
            onDismissRequest = { showSkipHabitDialog = false },
            title = { Text("Skip a habit today") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(skippable, key = { it.id }) { habit ->
                        ListItem(
                            headlineContent = { Text(habit.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onSkipHabitToday(habit.id, habit.targetValue)
                                    showSkipHabitDialog = false
                                },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSkipHabitDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun BedtimeAndReminderSection(
    bedtimeEnabled: Boolean,
    bedtimeStart: String,
    bedtimeEnd: String,
    onBedtimeChanged: (Boolean, String, String) -> Unit,
    morningReminderEnabled: Boolean,
    morningReminderTime: String,
    onMorningReminderChanged: (Boolean, String) -> Unit,
    proofOfLifeEnabled: Boolean,
    proofOfLifeTime: String,
    proofOfLifeWindowMinutes: Int,
    onProofOfLifeChanged: (Boolean, String, Int) -> Unit,
) {
    var start by remember(bedtimeStart) { mutableStateOf(bedtimeStart) }
    var end by remember(bedtimeEnd) { mutableStateOf(bedtimeEnd) }
    var reminderTime by remember(morningReminderTime) { mutableStateOf(morningReminderTime) }
    var checkInTime by remember(proofOfLifeTime) { mutableStateOf(proofOfLifeTime) }

    ListItem(
        headlineContent = { Text("Enable bedtime lock") },
        supportingContent = { Text("A hard curfew -- no habit or grace token unlocks it") },
        trailingContent = {
            Switch(
                checked = bedtimeEnabled,
                onCheckedChange = { onBedtimeChanged(it, start, end) },
            )
        },
    )
    if (bedtimeEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = start,
                onValueChange = { start = it; onBedtimeChanged(bedtimeEnabled, it, end) },
                label = { Text("Start (HH:mm)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = end,
                onValueChange = { end = it; onBedtimeChanged(bedtimeEnabled, start, it) },
                label = { Text("End (HH:mm)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

    SectionHeader("Daily todos")
    ListItem(
        headlineContent = { Text("Morning reminder") },
        supportingContent = { Text("Notifies you to fill in today's one-off tasks") },
        trailingContent = {
            Switch(
                checked = morningReminderEnabled,
                onCheckedChange = { onMorningReminderChanged(it, reminderTime) },
            )
        },
    )
    if (morningReminderEnabled) {
        OutlinedTextField(
            value = reminderTime,
            onValueChange = { reminderTime = it; onMorningReminderChanged(morningReminderEnabled, it) },
            label = { Text("Time (HH:mm)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

    SectionHeader("Morning check-in")
    Text(
        "A daily photo proving you're up -- miss the window and apps stay locked " +
            "${ProofOfLifeRepository.PENALTY_MINUTES} minutes longer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ListItem(
        headlineContent = { Text("Enable check-in") },
        trailingContent = {
            Switch(
                checked = proofOfLifeEnabled,
                onCheckedChange = { onProofOfLifeChanged(it, checkInTime, proofOfLifeWindowMinutes) },
            )
        },
    )
    if (proofOfLifeEnabled) {
        OutlinedTextField(
            value = checkInTime,
            onValueChange = { checkInTime = it; onProofOfLifeChanged(proofOfLifeEnabled, it, proofOfLifeWindowMinutes) },
            label = { Text("Time (HH:mm)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Grace window",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(15, 30, 60).forEach { minutes ->
                FilterChip(
                    selected = proofOfLifeWindowMinutes == minutes,
                    onClick = { onProofOfLifeChanged(proofOfLifeEnabled, checkInTime, minutes) },
                    label = { Text("$minutes min") },
                )
            }
        }
    }
}

/** Whole days remaining until [untilEpochMillis], rounded up so "a few hours left" still reads as 1, not 0. 0 once it's passed. */
private fun daysUntil(untilEpochMillis: Long): Int {
    val millisLeft = untilEpochMillis - System.currentTimeMillis()
    if (millisLeft <= 0) return 0
    return ((millisLeft + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt()
}

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Where the user pastes their own Anthropic API key so photo-verification habits can verify photos. */
@Composable
private fun ApiKeyField(apiKey: String?, onApiKeyChanged: (String) -> Unit) {
    var text by remember(apiKey) { mutableStateOf(apiKey.orEmpty()) }
    var visible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Habits with photo verification use your own Anthropic API key to check proof photos. " +
                "Get one at console.anthropic.com.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onApiKeyChanged(it) },
            label = { Text("Anthropic API key") },
            placeholder = { Text("sk-ant-...") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Hide key" else "Show key",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Lets Walk-steps and Exercise habits sync from Health Connect instead of being logged
 * by hand, mirroring the "use an app for N minutes" habit's automatic tracking. Only
 * shown when [HealthConnectManager.isAvailable] -- the whole section is a no-op on a
 * device without the provider installed.
 */
@Composable
private fun HealthConnectSection(
    permissionsGranted: Boolean,
    syncEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onSyncToggled: (Boolean) -> Unit,
) {
    Text(
        "Sync Walk steps and Exercise habits from Health Connect instead of logging them by hand.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ListItem(
        headlineContent = { Text("Read permissions") },
        supportingContent = { Text("Steps and exercise sessions, read-only") },
        trailingContent = {
            if (permissionsGranted) {
                Text(
                    stringResource(R.string.permission_granted),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                OutlinedButton(onClick = onRequestPermissions) {
                    Text(stringResource(R.string.permission_grant))
                }
            }
        },
    )
    ListItem(
        headlineContent = { Text("Sync automatically") },
        supportingContent = {
            Text(
                if (permissionsGranted) {
                    "Checks every 30 minutes; logging progress by hand still always works."
                } else {
                    "Grant read permissions above first."
                },
            )
        },
        trailingContent = {
            Switch(checked = syncEnabled, onCheckedChange = onSyncToggled, enabled = permissionsGranted)
        },
    )
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(if (granted) stringResource(R.string.permission_granted) else "Not granted")
        },
        trailingContent = {
            if (!granted) Icon(Icons.Filled.ChevronRight, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onClick),
    )
}
