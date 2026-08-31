package com.habitsfirst.androidclone.ui.apppicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
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
) : ViewModel() {

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val usageMinutes = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val query = MutableStateFlow("")
    private val sortMode = MutableStateFlow(AppSortMode.RECOMMENDED)
    private val isLoading = MutableStateFlow(true)

    val uiState: StateFlow<AppPickerUiState> = combine(
        // Paired first since kotlinx.coroutines.flow.combine tops out at 5 flows.
        combine(allApps, usageMinutes, ::Pair),
        blockedAppRepository.observeEnabledPackageNames(),
        query,
        sortMode,
        isLoading,
    ) { (apps, usage), blocked, q, sort, loading ->
        AppPickerUiState(
            isLoading = loading,
            apps = apps,
            blockedPackageNames = blocked.toSet(),
            query = q,
            sortMode = sort,
            usageMinutesByPackage = usage,
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
        viewModelScope.launch {
            blockedAppRepository.setBlocked(app.packageName, app.label, blocked)
        }
    }
}
