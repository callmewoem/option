package com.locke.app.ui.settings

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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locke.app.BuildConfig
import com.locke.app.R
import com.locke.app.data.healthconnect.HealthConnectManager
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.data.repository.ProofOfLifeRepository
import com.locke.app.domain.model.AccountabilityBuddy
import com.locke.app.domain.model.BuddyConnectionStatus
import com.locke.app.domain.model.HabitKind
import com.locke.app.domain.model.PremiumFeature
import com.locke.app.domain.model.SubscriptionTier
import com.locke.app.domain.model.ThemeVariant
import com.locke.app.ui.components.LockeGhostButton
import com.locke.app.ui.components.LockePrimaryButton
import com.locke.app.ui.components.icon
import com.locke.app.ui.habits.StatsRange
import com.locke.app.ui.theme.LockeColor
import com.locke.app.util.PermissionUtils
import com.locke.app.util.exportShareIntent
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Index ordered hardest-to-change things first (design spec §9): hard mode and the
 * locks it freezes, then the block list, then curfew/check-in, then everything easier
 * to walk back -- habits, rewards, and account-level odds and ends last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onManageApps: () -> Unit,
    onManageUrls: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPaywall: (PremiumFeature?) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
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
    val accountabilityMessage by viewModel.accountabilityMessage.collectAsStateWithLifecycle()
    LaunchedEffect(accountabilityMessage) {
        accountabilityMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onAccountabilityMessageShown()
        }
    }

    // A finished export launches the share sheet as a side effect, then clears itself
    // so rotating the screen (or coming back to it) doesn't relaunch the chooser.
    val exportRequest by viewModel.exportRequest.collectAsStateWithLifecycle()
    LaunchedEffect(exportRequest) {
        exportRequest?.let {
            context.startActivity(exportShareIntent(it))
            viewModel.onExportRequestHandled()
        }
    }
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    LaunchedEffect(exportError) {
        exportError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onExportErrorShown()
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccountabilityData() }

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            // Account-level, not habit-difficulty -- shown first regardless of the
            // "hardest to change first" ordering below, same reason a subscription
            // status usually sits at the top of any app's settings.
            item { SectionHeader("Premium") }
            item {
                PremiumSection(
                    isPremium = state.isPremium,
                    tier = state.subscriptionTier,
                    onUpgrade = { onOpenPaywall(null) },
                )
            }

            // -- Hardest to change first ---------------------------------------------
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
            item {
                ListItem(
                    headlineContent = { Text("Limited unblocking") },
                    supportingContent = {
                        Text(
                            if (state.limitedUnblockEnabled) {
                                "Once today's habits are done, blocked apps and sites stay open for " +
                                    "${state.limitedUnblockWindowMinutes} minutes, then lock again."
                            } else {
                                "Off -- blocked apps and sites stay open the rest of the day once habits are done."
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = state.limitedUnblockEnabled,
                            onCheckedChange = viewModel::onLimitedUnblockToggled,
                        )
                    },
                )
                if (state.limitedUnblockEnabled) {
                    MinutesStepperRow(
                        label = "Window length",
                        value = state.limitedUnblockWindowMinutes,
                        step = 5,
                        range = PreferencesRepository.MIN_LIMITED_UNBLOCK_WINDOW_MINUTES..
                            PreferencesRepository.MAX_LIMITED_UNBLOCK_WINDOW_MINUTES,
                        onValueChange = viewModel::onLimitedUnblockWindowMinutesChanged,
                    )
                    ListItem(
                        headlineContent = { Text("Streak bonus") },
                        supportingContent = { Text("Adds extra minutes to the window for every day of your current streak") },
                        trailingContent = {
                            Switch(
                                checked = state.limitedUnblockStreakBonusEnabled,
                                onCheckedChange = {
                                    viewModel.onLimitedUnblockStreakBonusChanged(it, state.limitedUnblockStreakBonusMinutesPerDay)
                                },
                            )
                        },
                    )
                    if (state.limitedUnblockStreakBonusEnabled) {
                        MinutesStepperRow(
                            label = "Bonus minutes per streak day",
                            value = state.limitedUnblockStreakBonusMinutesPerDay,
                            step = 1,
                            range = PreferencesRepository.MIN_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY..
                                PreferencesRepository.MAX_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY,
                            onValueChange = {
                                viewModel.onLimitedUnblockStreakBonusChanged(state.limitedUnblockStreakBonusEnabled, it)
                            },
                        )
                    }
                }
                HorizontalDivider()
            }

            item { SectionHeader("Bedtime + check-in") }
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
                    weeklyDigestEnabled = state.weeklyDigestEnabled,
                    weeklyDigestDayOfWeek = state.weeklyDigestDayOfWeek,
                    weeklyDigestTime = state.weeklyDigestTime,
                    onWeeklyDigestChanged = viewModel::onWeeklyDigestChanged,
                )
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

            // -- Easier to change ------------------------------------------------------
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

            item { SectionHeader("Rewards") }
            item {
                ListItem(
                    headlineContent = { Text("Grace tokens") },
                    supportingContent = { Text("1-minute unblock, redeemed from a lock screen") },
                    leadingContent = { Icon(Icons.Filled.Redeem, contentDescription = null, tint = LockeColor.Brass) },
                    trailingContent = { Text("${state.graceTokenCount}", style = MaterialTheme.typography.titleMedium, color = LockeColor.Brass) },
                )
                ListItem(
                    headlineContent = { Text("Task-skip tokens") },
                    supportingContent = { Text("Force-completes one gating habit for today") },
                    leadingContent = { Icon(Icons.Filled.Redeem, contentDescription = null, tint = LockeColor.Brass) },
                    trailingContent = { Text("${state.taskSkipTokenCount}", style = MaterialTheme.typography.titleMedium, color = LockeColor.Brass) },
                )
                if (state.taskSkipTokenCount > 0) {
                    LockeGhostButton(
                        text = "Skip a habit today",
                        onClick = { showSkipHabitDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }

            item { SectionHeader("Cosmetics") }
            item {
                Text(
                    "Kept, not worn -- the palette itself never changes. A collected record of what's been " +
                        "won from the daily lootbox, or unlocked instantly with a code below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
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
                            selected = unlocked,
                            onClick = {},
                            enabled = false,
                            label = { Text(variant.displayName) },
                            leadingIcon = if (!unlocked) {
                                { Icon(Icons.Filled.Lock, contentDescription = "Not yet kept", modifier = Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = themeCodeInput,
                        onValueChange = { themeCodeInput = it },
                        label = { Text("Cosmetic code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    LockePrimaryButton(
                        text = "Redeem",
                        onClick = {
                            viewModel.onRedeemThemeCode(themeCodeInput)
                            themeCodeInput = ""
                        },
                        enabled = themeCodeInput.isNotBlank(),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
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

            item { SectionHeader("Photo checking") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Habits with photo verification, and the morning check-in, send the submitted " +
                            "photo (plus that habit's own description and example photo, if any) to Locke's " +
                            "backend, which checks it with an AI model -- nothing is configured here anymore, it just works.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    when (val remaining = state.freeVerificationsRemaining) {
                        null -> Text(
                            "Premium: unlimited checks.",
                            style = MaterialTheme.typography.labelMedium,
                            color = LockeColor.Brass,
                        )
                        else -> Text(
                            "Free plan: $remaining of ${PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH} checks left this month.",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (remaining > 0) LockeColor.Brass else MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.freeVerificationsRemaining == 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LockeGhostButton(text = "Upgrade to Premium", onClick = { onOpenPaywall(PremiumFeature.PHOTO_VERIFICATION) })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }

            item { SectionHeader("Buddies") }
            item {
                AccountabilitySection(
                    canAddBuddy = state.canAddBuddy,
                    onUpgrade = { onOpenPaywall(PremiumFeature.ACCOUNTABILITY_BUDDY) },
                    pairingCode = state.myPairingCode,
                    onRegenerateCode = viewModel::onRegeneratePairingCode,
                    onAddBuddy = viewModel::onAddBuddy,
                    buddies = state.buddies,
                    shareStatsEnabled = state.shareDailyStatsEnabled,
                    onShareStatsToggled = viewModel::onShareDailyStatsToggled,
                )
            }

            item { SectionHeader("Export my data") }
            item {
                DataExportSection(
                    selectedRange = state.exportRange,
                    isExporting = state.isExporting,
                    onRangeSelected = viewModel::onExportRangeSelected,
                    onExportCsv = viewModel::onExportCsvClicked,
                    onExportJson = viewModel::onExportJsonClicked,
                )
            }

            item { SectionHeader("Privacy") }
            item {
                ListItem(
                    headlineContent = { Text("Share anonymous usage analytics") },
                    supportingContent = { Text("No photos, habit names, or anything else you type -- see the privacy policy for exactly what's sent.") },
                    trailingContent = {
                        Switch(checked = state.analyticsEnabled, onCheckedChange = viewModel::onAnalyticsEnabledChanged)
                    },
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPrivacyPolicy),
                )
                HorizontalDivider()
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                ListItem(
                    headlineContent = { Text("Diagnostics") },
                    supportingContent = { Text("Plain-language status and a fix button for every tracking failure") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenDiagnostics),
                )
                HorizontalDivider()
            }
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
    weeklyDigestEnabled: Boolean,
    weeklyDigestDayOfWeek: DayOfWeek,
    weeklyDigestTime: String,
    onWeeklyDigestChanged: (Boolean, DayOfWeek, String) -> Unit,
) {
    var start by remember(bedtimeStart) { mutableStateOf(bedtimeStart) }
    var end by remember(bedtimeEnd) { mutableStateOf(bedtimeEnd) }
    var reminderTime by remember(morningReminderTime) { mutableStateOf(morningReminderTime) }
    var checkInTime by remember(proofOfLifeTime) { mutableStateOf(proofOfLifeTime) }
    var digestTime by remember(weeklyDigestTime) { mutableStateOf(weeklyDigestTime) }

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
            "Window before the penalty lands",
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

    SectionHeader("Weekly digest")
    ListItem(
        headlineContent = { Text("Weekly recap") },
        supportingContent = { Text("A once-a-week nudge, e.g. \"5/7 days complete, best streak 4 days\"") },
        trailingContent = {
            Switch(
                checked = weeklyDigestEnabled,
                onCheckedChange = { onWeeklyDigestChanged(it, weeklyDigestDayOfWeek, digestTime) },
            )
        },
    )
    if (weeklyDigestEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DayOfWeek.values().forEach { day ->
                FilterChip(
                    selected = day == weeklyDigestDayOfWeek,
                    onClick = { onWeeklyDigestChanged(weeklyDigestEnabled, day, digestTime) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
        }
        OutlinedTextField(
            value = digestTime,
            onValueChange = { digestTime = it; onWeeklyDigestChanged(weeklyDigestEnabled, weeklyDigestDayOfWeek, it) },
            label = { Text("Time (HH:mm)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
        )
    }
}

/** Whole days remaining until [untilEpochMillis], rounded up so "a few hours left" still reads as 1, not 0. 0 once it's passed. */
private fun daysUntil(untilEpochMillis: Long): Int {
    val millisLeft = untilEpochMillis - System.currentTimeMillis()
    if (millisLeft <= 0) return 0
    return ((millisLeft + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt()
}

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

/** A "Label  [-] N min [+]" row for a minutes value stepped by [step] and clamped to [range]. */
@Composable
private fun MinutesStepperRow(label: String, value: Int, step: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
        }
        Text(
            "$value min",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label")
        }
    }
}

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

/** Subscription status + CTA -- the first thing shown in Settings (see the call site above for why it breaks from "hardest to change first"). */
@Composable
private fun PremiumSection(isPremium: Boolean, tier: SubscriptionTier, onUpgrade: () -> Unit) {
    ListItem(
        headlineContent = { Text(if (isPremium) "Locke Premium" else "Free plan") },
        supportingContent = {
            Text(
                if (isPremium) {
                    "You're on the ${tier.displayName} plan -- unlimited habits, AI photo checks, and buddies."
                } else {
                    "5 gating habits, 3 AI photo checks a month, 1 buddy -- Premium removes all three limits."
                },
            )
        },
        leadingContent = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = LockeColor.Brass) },
        trailingContent = { if (!isPremium) LockeGhostButton(text = "Upgrade", onClick = onUpgrade) },
    )
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
}

/**
 * Accountability buddies, backed by Locke's own backend (`backend/`) -- see
 * `data/repository/AccountabilityRepository.kt`. Free tier gets one real buddy
 * connection -- your pairing code, any buddy you've already added, and sharing your
 * own stats with them all keep working regardless; only the "add another" control is
 * swapped for an upgrade CTA once [canAddBuddy] is false, so a free user who's used
 * their one connection still sees it working, not a wall.
 */
@Composable
private fun AccountabilitySection(
    canAddBuddy: Boolean,
    onUpgrade: () -> Unit,
    pairingCode: String?,
    onRegenerateCode: () -> Unit,
    onAddBuddy: (String) -> Unit,
    buddies: List<AccountabilityBuddy>,
    shareStatsEnabled: Boolean,
    onShareStatsToggled: (Boolean) -> Unit,
) {
    var addBuddyCode by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Pair with a friend to share your daily progress and see theirs, synced through Locke's " +
                "own backend. Free plan: ${PreferencesRepository.MAX_FREE_BUDDIES} buddy. Offline-first -- " +
                "what's shown below is whatever was last synced, labeled as such.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    ListItem(
        headlineContent = { Text("Your pairing code") },
        supportingContent = { Text(pairingCode ?: "Not generated yet") },
        trailingContent = {
            LockeGhostButton(text = "Regenerate", onClick = onRegenerateCode)
        },
    )

    if (canAddBuddy) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = addBuddyCode,
                onValueChange = { addBuddyCode = it },
                label = { Text("Add a buddy") },
                placeholder = { Text("Their pairing code") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            LockePrimaryButton(text = "Add", onClick = { onAddBuddy(addBuddyCode); addBuddyCode = "" })
        }
    } else {
        ListItem(
            headlineContent = { Text("Free plan buddy limit reached") },
            supportingContent = { Text("Upgrade to Premium to add more buddies.") },
            trailingContent = { LockeGhostButton(text = "Upgrade", onClick = onUpgrade) },
        )
    }

    if (buddies.isNotEmpty()) {
        buddies.forEach { buddy ->
            ListItem(
                headlineContent = { Text(buddy.displayName) },
                supportingContent = { Text(buddySyncStatusLabel(buddy.status)) },
                trailingContent = {
                    buddy.lastSummary?.let { summary ->
                        Text(
                            "${summary.habitsCompleted}/${summary.totalHabits} today -- ${summary.currentStreak}d streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }

    ListItem(
        headlineContent = { Text("Share my daily stats") },
        supportingContent = { Text("Lets your buddies see today's progress and streak") },
        trailingContent = {
            Switch(checked = shareStatsEnabled, onCheckedChange = onShareStatsToggled)
        },
    )
}

/** Stale data is named plainly, not hidden or silently retried (design spec §5). */
private fun buddySyncStatusLabel(status: BuddyConnectionStatus): String = when (status) {
    BuddyConnectionStatus.Pending -> "Pending -- no sync yet"
    BuddyConnectionStatus.Connected -> "Connected"
    is BuddyConnectionStatus.Error -> "Showing the last synced data -- can't reach the backend: ${status.message}"
}

/**
 * Lets Steps, Workout, and Sleep habits sync from Health Connect instead of being
 * logged by hand, mirroring the "use an app for N minutes" habit's automatic tracking.
 * Only shown when [HealthConnectManager.isAvailable] -- the whole section is a no-op on
 * a device without the provider installed.
 */
@Composable
private fun HealthConnectSection(
    permissionsGranted: Boolean,
    syncEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onSyncToggled: (Boolean) -> Unit,
) {
    Text(
        "Sync Steps, Workout, and Sleep habits from Health Connect instead of logging them by hand.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ListItem(
        headlineContent = { Text("Read permissions") },
        supportingContent = { Text("Step count, workout duration, sleep duration -- read-only") },
        trailingContent = {
            if (permissionsGranted) {
                Text(
                    stringResource(R.string.permission_granted),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                LockeGhostButton(text = stringResource(R.string.permission_grant), onClick = onRequestPermissions)
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

/**
 * Exports habit/todo/streak history over [selectedRange] as CSV or JSON, for
 * self-review or sharing with e.g. a psychiatrist -- see [com.locke.app.util.StatsExportUtil].
 */
@Composable
private fun DataExportSection(
    selectedRange: StatsRange,
    isExporting: Boolean,
    onRangeSelected: (StatsRange) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
) {
    Text(
        "Export your habit, todo, and streak history to review yourself or share elsewhere.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.label) },
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LockeGhostButton(text = "Export as CSV", onClick = onExportCsv, enabled = !isExporting, modifier = Modifier.weight(1f))
        LockeGhostButton(text = "Export as JSON", onClick = onExportJson, enabled = !isExporting, modifier = Modifier.weight(1f))
    }
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
