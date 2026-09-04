package com.habitsfirst.androidclone.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.InstalledApp
import com.habitsfirst.androidclone.service.WorkScheduler
import com.habitsfirst.androidclone.ui.habit.defaultTarget
import com.habitsfirst.androidclone.util.DateProvider
import com.habitsfirst.androidclone.util.InstalledAppsProvider
import com.habitsfirst.androidclone.util.PermissionUtils
import com.habitsfirst.androidclone.util.RecommendedApps
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One of the ready-made habits offered during onboarding. */
data class HabitTemplate(
    val name: String,
    val type: HabitType,
    val targetValue: Int = type.defaultTarget(),
)

val onboardingHabitTemplates = listOf(
    HabitTemplate("Walk 10,000 steps", HabitType.STEPS, 10_000),
    HabitTemplate("Work out", HabitType.TIMED_MINUTES, 30),
    HabitTemplate("Meditate", HabitType.TIMED_MINUTES, 10),
    HabitTemplate("Read", HabitType.TALLY),
    HabitTemplate("Make my bed", HabitType.TALLY),
)

data class OnboardingUiState(
    val installedApps: List<InstalledApp> = emptyList(),
    val selectedPackageNames: Set<String> = emptySet(),
    /**
     * Today's per-app foreground minutes, read once usage access is granted (empty
     * otherwise). Drives [recommendedPackageNames] and [sortedInstalledApps] so the
     * app-picking step reflects this phone's actual screen time instead of a guess.
     */
    val usageMinutesByPackage: Map<String, Int> = emptyMap(),
    val hasUsageAccess: Boolean = false,
    /**
     * Selected habit templates, ordered easiest-first. With 2+ selected, this order
     * becomes the "ease into it" ramp: only the first gates right away, and the rest
     * are promoted one at a time as the current one becomes a consistent streak (see
     * [com.habitsfirst.androidclone.data.repository.EaseInRepository]).
     */
    val selectedTemplateOrder: List<HabitTemplate> = listOf(onboardingHabitTemplates[0], onboardingHabitTemplates[2]),
    val isFinishing: Boolean = false,
    val finished: Boolean = false,
) {
    val canContinueFromApps: Boolean get() = true // blocking zero apps is a valid (if pointless) choice
    val canContinueFromHabits: Boolean get() = selectedTemplateOrder.isNotEmpty()

    /** Packages flagged "Recommended" -- screen time first, the curated list as fallback. See [RecommendedApps.recommendedPackages]. */
    val recommendedPackageNames: Set<String> get() = RecommendedApps.recommendedPackages(usageMinutesByPackage)

    fun isRecommended(app: InstalledApp): Boolean = app.packageName in recommendedPackageNames

    /** Minutes of today's screen time for [app], or null if there's no usage data for it. */
    fun usageMinutesFor(app: InstalledApp): Int? = usageMinutesByPackage[app.packageName]

    /**
     * [installedApps] ordered for the picker: recommended apps first (screen-time
     * heavy hitters, or the curated list once there's no usage data to rank by),
     * ties broken by today's actual minutes, then alphabetically.
     */
    val sortedInstalledApps: List<InstalledApp>
        get() {
            val recommended = recommendedPackageNames
            return installedApps.sortedWith(
                compareByDescending<InstalledApp> { it.packageName in recommended }
                    .thenByDescending { usageMinutesByPackage[it.packageName] ?: 0 }
                    .thenBy { it.label.lowercase() },
            )
        }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val blockedAppRepository: BlockedAppRepository,
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Raw list only -- OnboardingUiState.sortedInstalledApps applies the
            // recommended-first ordering, re-sorting live once usage minutes arrive.
            val apps = installedAppsProvider.getLaunchableApps()
            _uiState.value = _uiState.value.copy(installedApps = apps)
        }
        refreshUsageAccessState()
    }

    /**
     * Re-checks usage-access and, if granted, re-reads today's per-app minutes.
     * Called on init and whenever the usage-access step resumes (e.g. back from the
     * system settings screen the user just granted it in), so a grant made mid-onboarding
     * shows up as screen-time-based recommendations without needing a restart.
     */
    fun refreshUsageAccessState() {
        viewModelScope.launch {
            val granted = PermissionUtils.hasUsageAccess(appContext)
            val minutes = if (granted) installedAppsProvider.getTodayUsageMinutes() else emptyMap()
            _uiState.value = _uiState.value.copy(hasUsageAccess = granted, usageMinutesByPackage = minutes)
        }
    }

    fun onAppToggled(packageName: String, selected: Boolean) {
        val current = _uiState.value.selectedPackageNames
        _uiState.value = _uiState.value.copy(
            selectedPackageNames = if (selected) current + packageName else current - packageName,
        )
    }

    /** A newly-checked template joins the end of the order (hardest, until reordered). */
    fun onTemplateToggled(template: HabitTemplate, selected: Boolean) {
        val current = _uiState.value.selectedTemplateOrder
        val updated = when {
            selected && template !in current -> current + template
            !selected -> current - template
            else -> current
        }
        _uiState.value = _uiState.value.copy(selectedTemplateOrder = updated)
    }

    /** Moves [template] one spot easier (delta = -1) or harder (delta = +1) in the ease-in order. */
    fun onTemplateReordered(template: HabitTemplate, delta: Int) {
        val current = _uiState.value.selectedTemplateOrder.toMutableList()
        val index = current.indexOf(template)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, current.lastIndex)
        if (target == index) return
        current.add(target, current.removeAt(index))
        _uiState.value = _uiState.value.copy(selectedTemplateOrder = current)
    }

    fun finishOnboarding() {
        if (_uiState.value.isFinishing) return
        _uiState.value = _uiState.value.copy(isFinishing = true)
        viewModelScope.launch {
            val state = _uiState.value
            val appsByPackage = state.installedApps.associateBy { it.packageName }
            state.selectedPackageNames.forEach { packageName ->
                val label = appsByPackage[packageName]?.label ?: packageName
                blockedAppRepository.setBlocked(packageName, label, blocked = true)
            }

            // One habit has nothing to ease into -- it just gates immediately, same as
            // before. Two or more ramp in by the chosen order: only the easiest gates
            // right away, the rest start TRACKED and are promoted one at a time.
            val templates = state.selectedTemplateOrder
            val isRamp = templates.size > 1
            templates.forEachIndexed { index, template ->
                habitRepository.saveHabit(
                    Habit(
                        name = template.name,
                        type = template.type,
                        targetValue = template.targetValue,
                        kind = if (!isRamp || index == 0) HabitKind.GATING else HabitKind.TRACKED,
                        easeInOrder = if (isRamp) index else null,
                    ),
                )
            }

            preferencesRepository.setOnboardingComplete(true)
            preferencesRepository.setOnboardingCompletedDate(DateProvider.todayString())
            WorkScheduler.scheduleUsageTracking(appContext)
            WorkScheduler.scheduleMorningTodoReminder(appContext)
            WorkScheduler.scheduleProofOfLifeCheck(appContext)
            WorkScheduler.scheduleBlocklistRefresh(appContext)
            _uiState.value = _uiState.value.copy(isFinishing = false, finished = true)
        }
    }
}
