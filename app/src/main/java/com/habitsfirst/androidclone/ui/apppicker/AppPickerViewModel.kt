package com.habitsfirst.androidclone.ui.apppicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.domain.model.InstalledApp
import com.habitsfirst.androidclone.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppPickerUiState(
    val isLoading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val blockedPackageNames: Set<String> = emptySet(),
    val query: String = "",
) {
    val filteredApps: List<InstalledApp>
        get() = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
}

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val blockedAppRepository: BlockedAppRepository,
) : ViewModel() {

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val query = MutableStateFlow("")
    private val isLoading = MutableStateFlow(true)

    val uiState: StateFlow<AppPickerUiState> = combine(
        allApps,
        blockedAppRepository.observeEnabledPackageNames(),
        query,
        isLoading,
    ) { apps, blocked, q, loading ->
        AppPickerUiState(
            isLoading = loading,
            apps = apps,
            blockedPackageNames = blocked.toSet(),
            query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPickerUiState())

    init {
        viewModelScope.launch {
            allApps.value = installedAppsProvider.getLaunchableApps()
            isLoading.value = false
        }
    }

    fun onQueryChanged(newQuery: String) {
        query.value = newQuery
    }

    fun onToggleApp(app: InstalledApp, blocked: Boolean) {
        viewModelScope.launch {
            blockedAppRepository.setBlocked(app.packageName, app.label, blocked)
        }
    }
}
