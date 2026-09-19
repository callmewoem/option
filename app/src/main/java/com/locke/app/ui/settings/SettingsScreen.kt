package com.locke.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.locke.app.BuildConfig
import com.locke.app.R
import com.locke.app.data.healthconnect.HealthConnectManager
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.data.repository.ProofOfLifeRepository
import com.locke.app.domain.model.AccountabilityBuddy
import com.locke.app.domain.model.BuddyConnectionStatus
import com.locke.app.domain.model.Habit
import com.locke.app.domain.model.HabitKind
import com.locke.app.domain.model.HabitList
import com.locke.app.domain.model.PremiumFeature
import com.locke.app.domain.model.SubscriptionTier
import com.locke.app.domain.model.ThemeMode
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
 * Grouped into collapsible sections, ordered hardest-to-change things first (design spec
 * §9): Premium status stays pinned open at top (account-level, not habit-difficulty --
 * same reason a subscription status usually sits at the top of any app's settings), then
 * hard mode, then everything easier to walk back -- blocking, habits, rewards, and
 * account-level odds and ends last. Every section but Premium and hard mode starts
 * collapsed so the screen reads as a short table of contents instead of one long wall
 * of controls.
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
    var showNewHabitListDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
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
            // -- Account-level and hardest to change first, always visible -------------
            item {
                PremiumSection(
                    isPremium = state.isPremium,
                    tier = state.subscriptionTier,
                    onUpgrade = { onOpenPaywall(null) },
                )
            }

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
                HorizontalDivider()
            }

            // -- Everything else, collapsed into tappable groups -----------------------
            item {
                ExpandableSection(
                    title = stringResource(R.string.settings_blocked_apps),
                    icon = Icons.Filled.Lock,
                    summary = blockingSummary(state),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.home_manage_apps)) },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onManageApps),
                    )
                    ListItem(
                        headlineContent = { Text("Blocked websites") },
                        supportingContent = { Text("Premade porn/social lists, plus your own custom lists") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onManageUrls),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                }
            }

            item {
                ExpandableSection(
                    title = "Bedtime + check-in",
                    icon = Icons.Filled.Notifications,
                    summary = reminderSummary(state),
                ) {
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
            }

            item {
                ExpandableSection(
                    title = stringResource(R.string.settings_habits),
                    icon = Icons.Filled.CheckCircle,
                    summary = habitsSummary(state),
                ) {
                    Text(
                        "How many consistent days before onboarding's next habit unlocks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    DropdownSelector(
                        label = "Ease-in days",
                        selected = state.easeInStreakLength,
                        options = listOf(3, 5, 7),
                        optionLabel = { "$it days" },
                        onSelected = viewModel::onEaseInStreakLengthChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    HabitListFilterRow(
                        lists = state.habitLists,
                        selectedListId = state.selectedHabitListFilter,
                        onSelect = viewModel::onSelectHabitListFilter,
                        onDeleteSelected = viewModel::onDeleteHabitList,
                        onAddList = { showNewHabitListDialog = true },
                    )
                    val sections = habitSections(state.habits, state.habitLists, state.selectedHabitListFilter)
                    sections.forEach { (list, habitsInSection) ->
                        if (list != null && sections.size > 1) {
                            Text(
                                text = list.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                            )
                        } else if (list == null && sections.size > 1) {
                            Text(
                                text = "Unsorted",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                            )
                        }
                        habitsInSection.forEach { habit ->
                            key(habit.id) {
                                val index = state.habits.indexOfFirst { it.id == habit.id }
                                var showListMenu by remember { mutableStateOf(false) }
                                ListItem(
                                    headlineContent = { Text(habit.name) },
                                    supportingContent = {
                                        val target = habit.displayTarget.ifBlank { "Custom check-in" }
                                        val schedule = if (habit.isDaily) null else " · ${habit.scheduleLabel}"
                                        Text("${habit.kind.label} · $target${schedule.orEmpty()}")
                                    },
                                    leadingContent = { Icon(habit.type.icon(), contentDescription = null) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box {
                                                IconButton(onClick = { showListMenu = true }) {
                                                    Icon(Icons.Filled.Label, contentDescription = "Add to another list")
                                                }
                                                DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text("No list") },
                                                        onClick = { viewModel.onMoveHabitToList(habit.id, null); showListMenu = false },
                                                    )
                                                    state.habitLists.forEach { habitList ->
                                                        DropdownMenuItem(
                                                            text = { Text(habitList.name) },
                                                            onClick = { viewModel.onMoveHabitToList(habit.id, habitList.id); showListMenu = false },
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = { viewModel.onMoveHabit(habit.id, up = true) },
                                                enabled = index > 0,
                                            ) {
                                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                                            }
                                            IconButton(
                                                onClick = { viewModel.onMoveHabit(habit.id, up = false) },
                                                enabled = index < state.habits.lastIndex,
                                            ) {
                                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                                            }
                                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onEditHabit(habit.id) },
                                )
                            }
                        }
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.home_add_habit)) },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddHabit),
                    )
                }
            }

            item {
                ExpandableSection(
                    title = "Rewards",
                    icon = Icons.Filled.Redeem,
                    summary = rewardsSummary(state),
                ) {
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
                }
            }

            item {
                ExpandableSection(
                    title = "Appearance",
                    icon = Icons.Filled.Palette,
                    summary = state.themeMode.displayName,
                ) {
                    Text(
                        "Applies to everyday screens -- Today, Stats, Settings, onboarding. Screens that lock, " +
                            "block, or demand something (curfew, penalties, morning check-in) always stay dark, " +
                            "regardless of this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    DropdownSelector(
                        label = "Theme",
                        selected = state.themeMode,
                        options = ThemeMode.entries,
                        optionLabel = { it.displayName },
                        onSelected = viewModel::onThemeModeChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (state.healthConnectAvailable) {
                item {
                    ExpandableSection(
                        title = "Health Connect",
                        icon = Icons.Filled.MonitorHeart,
                        summary = healthConnectSummary(state),
                    ) {
                        HealthConnectSection(
                            permissionsGranted = state.healthConnectPermissionsGranted,
                            syncEnabled = state.healthConnectSyncEnabled,
                            onRequestPermissions = { healthConnectPermissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
                            onSyncToggled = viewModel::onHealthConnectSyncToggled,
                        )
                    }
                }
            }

            item {
                ExpandableSection(
                    title = stringResource(R.string.settings_permissions),
                    icon = Icons.Filled.Security,
                    summary = permissionsSummary(hasUsageAccess, hasAccessibility, hasOverlay, state.notificationsEnabled),
                ) {
                    PermissionRow(
                        title = stringResource(R.string.permission_usage_access_title),
                        granted = hasUsageAccess,
                        onClick = { context.startActivity(PermissionUtils.usageAccessSettingsIntent(context)) },
                    )
                    PermissionRow(
                        title = stringResource(R.string.permission_accessibility_title),
                        granted = hasAccessibility,
                        onClick = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
                    )
                    PermissionRow(
                        title = stringResource(R.string.permission_overlay_title),
                        granted = hasOverlay,
                        onClick = { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) },
                    )
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
            }

            item {
                ExpandableSection(
                    title = "Photo checking",
                    icon = Icons.Filled.CameraAlt,
                    summary = photoCheckingSummary(state),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "Habits with photo verification, and the morning check-in, send the submitted " +
                                "photo (plus that habit's own description and example photo, if any) to Locke's " +
                                "backend, which checks it with an AI model -- nothing is configured here anymore, it just works.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (state.isPremium) {
                            Text(
                                "Premium: unlimited checks.",
                                style = MaterialTheme.typography.labelMedium,
                                color = LockeColor.Brass,
                            )
                        } else {
                            Text(
                                "Requires a Locke Premium subscription.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LockeGhostButton(text = "Start free trial", onClick = { onOpenPaywall(PremiumFeature.PHOTO_VERIFICATION) })
                        }
                    }
                }
            }

            item {
                ExpandableSection(
                    title = "Connections",
                    icon = Icons.Filled.Link,
                    summary = connectionsSummary(state),
                ) {
                    ConnectionKeyField(
                        title = "WakaTime",
                        description = "Powers \"Code (WakaTime)\" habits -- reads today's total coding time from " +
                            "your WakaTime account. Get a key at wakatime.com/settings/api-key.",
                        label = "WakaTime API key",
                        placeholder = "waka_...",
                        value = state.wakaTimeApiKey,
                        onValueChanged = viewModel::onWakaTimeApiKeyChanged,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ConnectionKeyField(
                        title = "GitHub",
                        description = "Optional for \"GitHub\" habits -- without a token, only public activity is " +
                            "checked at GitHub's standard rate limit. Add a personal access token (no scopes " +
                            "needed for public activity) to raise that limit and count private contributions too.",
                        label = "GitHub personal access token",
                        placeholder = "ghp_...",
                        value = state.githubToken,
                        onValueChanged = viewModel::onGithubTokenChanged,
                    )
                }
            }

            item {
                ExpandableSection(
                    title = "Buddies",
                    icon = Icons.Filled.People,
                    summary = buddiesSummary(state),
                ) {
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
            }

            item {
                ExpandableSection(
                    title = "Export my data",
                    icon = Icons.Filled.Download,
                    summary = state.exportRange.label,
                ) {
                    DataExportSection(
                        selectedRange = state.exportRange,
                        isExporting = state.isExporting,
                        onRangeSelected = viewModel::onExportRangeSelected,
                        onExportCsv = viewModel::onExportCsvClicked,
                        onExportJson = viewModel::onExportJsonClicked,
                    )
                }
            }

            item {
                ExpandableSection(
                    title = "Privacy",
                    icon = Icons.Filled.PrivacyTip,
                    summary = if (state.analyticsEnabled) "Analytics on" else "Analytics off",
                ) {
                    ListItem(
                        headlineContent = { Text("Share anonymous usage analytics") },
                        supportingContent = { Text("No photos, habit names, or anything else you type -- see the privacy policy for exactly what's sent.") },
                        trailingContent = {
                            Switch(checked = state.analyticsEnabled, onCheckedChange = viewModel::onAnalyticsEnabledChanged)
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Privacy Policy") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenPrivacyPolicy),
                    )
                }
            }

            item {
                ExpandableSection(
                    title = stringResource(R.string.settings_about),
                    icon = Icons.Filled.Info,
                    summary = "v${BuildConfig.VERSION_NAME}",
                ) {
                    ListItem(
                        headlineContent = { Text("Diagnostics") },
                        supportingContent = { Text("Plain-language status and a fix button for every tracking failure") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenDiagnostics),
                    )
                    ListItem(
                        headlineContent = { Text("Version") },
                        supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    )
                }
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

    if (showNewHabitListDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewHabitListDialog = false },
            title = { Text("New list") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. \"Morning routine\"") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onCreateHabitList(name); showNewHabitListDialog = false },
                    enabled = name.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewHabitListDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * A collapsible settings group: one tappable header line (icon, title, a live one-line
 * summary of its current state, and a chevron) that expands to reveal its controls.
 * Collapsed by default -- see [SettingsScreen] -- so the settings list reads as a short,
 * scannable table of contents instead of every control being on screen at once.
 */
@Composable
private fun ExpandableSection(
    title: String,
    icon: ImageVector,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = summary?.let {
                { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        )
        if (expanded) {
            Column(content = content)
        }
        HorizontalDivider()
    }
}

/**
 * A single-choice dropdown -- collapsed it reads "Label: current value", tapping opens a
 * menu of [options]. Used in place of a row of filter chips wherever a setting only ever
 * has one value active at a time, so the choice takes one line instead of a whole row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** "$granted/$total granted" for the collapsed Permissions header. */
private fun permissionsSummary(usage: Boolean, accessibility: Boolean, overlay: Boolean, notifications: Boolean): String {
    val granted = listOf(usage, accessibility, overlay, notifications).count { it }
    return "$granted/4 granted"
}

/** What's currently locking things down, for the collapsed Blocking header. */
private fun blockingSummary(state: SettingsUiState): String =
    if (state.limitedUnblockEnabled) {
        "Limited unblocking -- ${state.limitedUnblockWindowMinutes} min window"
    } else {
        "Apps and websites"
    }

/** Which of bedtime/check-in/reminder/digest are on, for the collapsed header. */
private fun reminderSummary(state: SettingsUiState): String {
    val parts = buildList {
        if (state.bedtimeEnabled) add("Bedtime ${state.bedtimeStart}–${state.bedtimeEnd}")
        if (state.proofOfLifeEnabled) add("Check-in ${state.proofOfLifeTime}")
        if (state.morningReminderEnabled) add("Reminder ${state.morningReminderTime}")
        if (state.weeklyDigestEnabled) {
            add("Digest ${state.weeklyDigestDayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}")
        }
    }
    return if (parts.isEmpty()) "All off" else parts.joinToString(" · ")
}

private fun habitsSummary(state: SettingsUiState): String {
    val count = state.habits.size
    return "$count ${if (count == 1) "habit" else "habits"} · eases in over ${state.easeInStreakLength} days"
}

/**
 * "All" plus every user-defined [HabitList], Habitica tag-filter style -- tapping one
 * narrows the Habits list below (and becomes the default list a new habit's own "+ List"
 * button pre-selects, see [com.locke.app.ui.habit.AddEditHabitScreen]); tapping the
 * already-selected one deselects back to "All". The selected list's own chip carries a
 * small delete affordance, since there's no other list-management screen -- deleting
 * only un-assigns its habits (see [com.locke.app.data.repository.HabitRepository.deleteList]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitListFilterRow(
    lists: List<HabitList>,
    selectedListId: Long?,
    onSelect: (Long?) -> Unit,
    onDeleteSelected: (HabitList) -> Unit,
    onAddList: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = selectedListId == null, onClick = { onSelect(null) }, label = { Text("All") })
        lists.forEach { list ->
            val selected = selectedListId == list.id
            FilterChip(
                selected = selected,
                onClick = { onSelect(if (selected) null else list.id) },
                label = { Text(list.name) },
            )
            if (selected) {
                IconButton(
                    onClick = { onDeleteSelected(list) },
                    modifier = Modifier.height(32.dp).width(32.dp),
                ) {
                    Icon(Icons.Filled.LabelOff, contentDescription = "Delete \"${list.name}\"", modifier = Modifier.height(16.dp))
                }
            }
        }
        FilterChip(selected = false, onClick = onAddList, label = { Text("+ List") })
    }
}

/**
 * Groups [habits] by [HabitList] for display, in [lists]' own manual order, with any
 * unassigned habits (or ones whose list was just deleted) trailing in an "Unsorted"
 * bucket -- a single unheaded section (see the [selectedListId] and empty-[lists] cases
 * below) renders exactly like the old flat list did, so this is a no-op visually until
 * there's more than one list. Filtering to one selected list collapses to that one
 * section, unheaded, since the chip row above already says which list is showing.
 */
private fun habitSections(
    habits: List<Habit>,
    lists: List<HabitList>,
    selectedListId: Long?,
): List<Pair<HabitList?, List<Habit>>> {
    val filtered = if (selectedListId == null) habits else habits.filter { it.listId == selectedListId }
    if (selectedListId != null || lists.isEmpty()) {
        val noHeader: HabitList? = null
        return listOf(noHeader to filtered)
    }

    val byId = lists.associateBy { it.id }
    val grouped = filtered.groupBy { byId[it.listId] }
    val ordered = mutableListOf<Pair<HabitList?, List<Habit>>>()
    lists.forEach { list -> grouped[list]?.let { ordered += list to it } }
    grouped[null]?.let { ordered += null to it }
    return ordered
}

private fun rewardsSummary(state: SettingsUiState): String =
    "${state.graceTokenCount} grace · ${state.taskSkipTokenCount} skip"

private fun healthConnectSummary(state: SettingsUiState): String = when {
    state.healthConnectSyncEnabled -> "Syncing"
    state.healthConnectPermissionsGranted -> "Permission granted"
    else -> "Not connected"
}

private fun photoCheckingSummary(state: SettingsUiState): String =
    if (state.isPremium) "Premium -- unlimited" else "Requires Premium"

private fun connectionsSummary(state: SettingsUiState): String {
    val connected = buildList {
        if (!state.wakaTimeApiKey.isNullOrBlank()) add("WakaTime")
        if (!state.githubToken.isNullOrBlank()) add("GitHub")
    }
    return if (connected.isEmpty()) "None connected" else connected.joinToString(", ")
}

private fun buddiesSummary(state: SettingsUiState): String {
    val count = state.buddies.size
    return if (count == 0) "Not paired yet" else "$count ${if (count == 1) "buddy" else "buddies"}"
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

    SubHeader("Morning check-in")
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
        DropdownSelector(
            label = "Window before the penalty lands",
            selected = proofOfLifeWindowMinutes,
            options = listOf(15, 30, 60),
            optionLabel = { "$it min" },
            onSelected = { onProofOfLifeChanged(proofOfLifeEnabled, checkInTime, it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

    SubHeader("Daily todos")
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

    SubHeader("Weekly digest")
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
        DropdownSelector(
            label = "Day of week",
            selected = weeklyDigestDayOfWeek,
            options = DayOfWeek.values().toList(),
            optionLabel = { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
            onSelected = { onWeeklyDigestChanged(weeklyDigestEnabled, it, digestTime) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
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

/** A small label for a sub-group inside an already-expanded [ExpandableSection]. */
@Composable
private fun SubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Subscription status + CTA -- pinned open at the top of Settings (see the call site above for why it breaks from "hardest to change first"). */
@Composable
private fun PremiumSection(isPremium: Boolean, tier: SubscriptionTier, onUpgrade: () -> Unit) {
    ListItem(
        headlineContent = { Text(if (isPremium) "Locke Premium" else "Not subscribed") },
        supportingContent = {
            Text(
                if (isPremium) {
                    "You're on the ${tier.displayName} plan -- unlimited habits, AI photo checks, and buddies."
                } else {
                    "Unlimited gating habits, AI photo checks, and accountability buddies -- start your free trial."
                },
            )
        },
        leadingContent = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = LockeColor.Brass) },
        trailingContent = { if (!isPremium) LockeGhostButton(text = "Start free trial", onClick = onUpgrade) },
    )
    HorizontalDivider()
}

/**
 * A "paste your own key" row for one third-party connection (WakaTime, GitHub) -- just
 * the key itself and a username/timestamp, no photo/content ever leaves the device for
 * either of these (unlike photo verification, which goes through Locke's own backend).
 */
@Composable
private fun ConnectionKeyField(
    title: String,
    description: String,
    label: String,
    placeholder: String,
    value: String?,
    onValueChanged: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.orEmpty()) }
    var visible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onValueChanged(it) },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
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
 * Accountability buddies, backed by Locke's own backend (`backend/`) -- see
 * `data/repository/AccountabilityRepository.kt`. Adding a buddy requires an active
 * subscription -- your pairing code, any buddy you've already added, and sharing your
 * own stats with them all keep working regardless of entitlement; only the "add
 * another" control is swapped for an upgrade CTA once [canAddBuddy] is false, so a
 * lapsed subscriber still sees their existing connection working, not a wall.
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
                "own backend. Requires a Locke Premium subscription. Offline-first -- " +
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
            headlineContent = { Text("Premium required") },
            supportingContent = { Text("Adding a buddy requires a Locke Premium subscription.") },
            trailingContent = { LockeGhostButton(text = "Start free trial", onClick = onUpgrade) },
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
    DropdownSelector(
        label = "Range",
        selected = selectedRange,
        options = StatsRange.entries,
        optionLabel = { it.label },
        onSelected = onRangeSelected,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LockeGhostButton(text = "Export as CSV", onClick = onExportCsv, enabled = !isExporting, modifier = Modifier.weight(1f))
        LockeGhostButton(text = "Export as JSON", onClick = onExportJson, enabled = !isExporting, modifier = Modifier.weight(1f))
    }
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
