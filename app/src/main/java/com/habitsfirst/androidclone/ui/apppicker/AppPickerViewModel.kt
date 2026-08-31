package com.habitsfirst.androidclone.ui.apppicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
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
    val blockedPackageNames: Set<String> = emptySet(),
    val query: String = "",
    val sortMode: AppSortMode = AppSortMode.RECOMMENDED,
    val usageMinutesByPackage: Map<String, Int> = emptyMap(),
    /** Hard mode: an already-blocked app can't be unblocked, only new ones added. */
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
        // Paired up since kotlinx.coroutines.flow.combine tops out at 5 flows.
        combine(allApps, usageMinutes, ::Pair),
        combine(blockedAppRepository.observeEnabledPackageNames(), preferencesRepository.isHardModeEnabled, ::Pair),
        query,
        sortMode,
        isLoading,
    ) { (apps, usage), (blocked, hardMode), q, sort, loading ->
        AppPickerUiState(
            isLoading = loading,
            apps = apps,
            blockedPackageNames = blocked.toSet(),
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

    fun onToggleApp(app: InstalledApp, blocked: Boolean) {
        // Hard mode: apps can be added to the block list but never removed from it.
        if (!blocked && uiState.value.isHardModeEnabled) return
        viewModelScope.launch {
            blockedAppRepository.setBlocked(app.packageName, app.label, blocked)
        }
    }
}
