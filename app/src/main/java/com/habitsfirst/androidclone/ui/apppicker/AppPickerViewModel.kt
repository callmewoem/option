package com.habitsfirst.androidclone.ui.apppicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.AppBlockMode
import com.habitsfirst.androidclone.domain.model.InstalledApp
import com.habitsfirst.androidclone.util.InstalledAppsProvider
import com.habitsfirst.androidclone.util.RecommendedApps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppSortMode(val label: String) {
    RECOMMENDED("Recommended"),
    MOST_USED("Most used"),
    ALPHABETICAL("A-Z"),
}

data class AppPickerUiState(
    val isLoading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    /** The picked packages -- locked apps in [AppBlockMode.BLACKLIST], always-allowed apps in [AppBlockMode.WHITELIST]. */
    val selectedPackageNames: Set<String> = emptySet(),
    val appBlockMode: AppBlockMode = AppBlockMode.BLACKLIST,
    val query: String = "",
    val sortMode: AppSortMode = AppSortMode.RECOMMENDED,
    val usageMinutesByPackage: Map<String, Int> = emptyMap(),
    /** Hard mode: restrictions can only tighten, never loosen -- see [isToggleLockedByHardMode]. */
    val isHardModeEnabled: Boolean = false,
) {
    val filteredApps: List<InstalledApp>
        get() {
            val base = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
            return when (sortMode) {
                AppSortMode.ALPHABETICAL -> base.sortedBy { it.label.lowercase() }
                AppSortMode.MOST_USED -> base.sortedByDescending { usageMinutesByPackage[it.packageName] ?: 0 }
                AppSortMode.RECOMMENDED -> base.sortedWith(
                    compareByDescending<InstalledApp> { RecommendedApps.isRecommended(it.packageName) }
                        .thenBy { it.label.lowercase() },
                )
            }
        }

    fun isRecommended(app: InstalledApp): Boolean = RecommendedApps.isRecommended(app.packageName)

    /**
     * Whether hard mode should keep this app's switch from being toggled, given whether
     * it's currently selected. The forbidden direction is whichever one loosens
     * restrictions, and that flips with the mode: in [AppBlockMode.BLACKLIST] a selected
     * (locked) app can't be deselected; in [AppBlockMode.WHITELIST] deselecting an app
     * only tightens (it joins everything else that's locked), so it's adding a new app
     * to the always-allowed set that's forbidden instead.
     */
    fun isToggleLockedByHardMode(isSelected: Boolean): Boolean {
        if (!isHardModeEnabled) return false
        return if (appBlockMode == AppBlockMode.BLACKLIST) isSelected else !isSelected
    }
}

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val blockedAppRepository: BlockedAppRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val usageMinutes = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val query = MutableStateFlow("")
    private val sortMode = MutableStateFlow(AppSortMode.RECOMMENDED)
    private val isLoading = MutableStateFlow(true)

    val uiState: StateFlow<AppPickerUiState> = combine(
        // Paired/tripled up since kotlinx.coroutines.flow.combine tops out at 5 flows.
        combine(allApps, usageMinutes, ::Pair),
        combine(
            blockedAppRepository.observeEnabledPackageNames(),
            preferencesRepository.isHardModeEnabled,
            preferencesRepository.appBlockMode,
            ::Triple,
        ),
        query,
        sortMode,
        isLoading,
    ) { (apps, usage), (selected, hardMode, blockMode), q, sort, loading ->
        AppPickerUiState(
            isLoading = loading,
            apps = apps,
            selectedPackageNames = selected.toSet(),
            appBlockMode = blockMode,
            query = q,
            sortMode = sort,
            usageMinutesByPackage = usage,
            isHardModeEnabled = hardMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPickerUiState())

    init {
        viewModelScope.launch {
            allApps.value = installedAppsProvider.getLaunchableApps()
            isLoading.value = false
        }
        viewModelScope.launch {
            usageMinutes.value = installedAppsProvider.getTodayUsageMinutes()
        }
    }

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
    }

    fun onSortModeChanged(mode: AppSortMode) {
        sortMode.value = mode
    }

    fun onModeChanged(mode: AppBlockMode) {
        viewModelScope.launch { preferencesRepository.setAppBlockMode(mode) }
    }

    fun onToggleApp(app: InstalledApp, selected: Boolean) {
        val isCurrentlySelected = app.packageName in uiState.value.selectedPackageNames
        if (uiState.value.isToggleLockedByHardMode(isCurrentlySelected)) return
        viewModelScope.launch {
            blockedAppRepository.setBlocked(app.packageName, app.label, selected)
        }
    }
}
