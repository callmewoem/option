package com.habitsfirst.androidclone.ui.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.service.AppUsageSyncReport
import com.habitsfirst.androidclone.service.AppUsageSyncer
import com.habitsfirst.androidclone.service.UsageTrackingWorker
import com.habitsfirst.androidclone.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val hasUsageAccess: Boolean = false,
    /** Every active habit of type [HabitType.APP_USAGE_MINUTES], regardless of whether it has a target app -- the full universe, for comparing against what a sync run actually processes. */
    val appUsageHabits: List<Habit> = emptyList(),
    val periodicWorkState: String = "checking…",
    val oneOffWorkState: String = "checking…",
    val lastAutoSyncAtEpochMillis: Long? = null,
    val lastAutoSyncHabitCount: Int = 0,
    val lastAutoSyncError: String? = null,
    val isRunningManualSync: Boolean = false,
    val manualSyncReport: AppUsageSyncReport? = null,
    /** Set only if [AppUsageSyncer.sync] itself threw rather than returning a report -- the single most useful thing this screen can surface. */
    val manualSyncCrash: String? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val appUsageSyncer: AppUsageSyncer,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()

        viewModelScope.launch {
            habitRepository.observeHabits().collect { habits ->
                _uiState.value = _uiState.value.copy(
                    appUsageHabits = habits.filter { it.type == HabitType.APP_USAGE_MINUTES },
                )
            }
        }

        viewModelScope.launch {
            val workManager = WorkManager.getInstance(appContext)
            combine(
                workManager.getWorkInfosForUniqueWorkFlow(UsageTrackingWorker.UNIQUE_PERIODIC_NAME),
                workManager.getWorkInfosForUniqueWorkFlow(UsageTrackingWorker.ONE_OFF_NAME),
                preferencesRepository.lastUsageSyncInfo,
            ) { periodic, oneOff, lastSync ->
                Triple(periodic.describeLatest(), oneOff.describeLatest(), lastSync)
            }.collect { (periodicState, oneOffState, lastSync) ->
                _uiState.value = _uiState.value.copy(
                    periodicWorkState = periodicState,
                    oneOffWorkState = oneOffState,
                    lastAutoSyncAtEpochMillis = lastSync.atEpochMillis,
                    lastAutoSyncHabitCount = lastSync.habitCount,
                    lastAutoSyncError = lastSync.error,
                )
            }
        }
    }

    /** Re-read on every visit -- usage access is granted from system Settings, outside this screen. */
    fun refreshPermissions() {
        _uiState.value = _uiState.value.copy(hasUsageAccess = PermissionUtils.hasUsageAccess(appContext))
    }

    /**
     * Runs [AppUsageSyncer.sync] directly, synchronously from this screen's own coroutine
     * scope -- the exact same code the background worker runs, but with the result (or a
     * crash) shown right here instead of only ever visible in Logcat.
     */
    fun runManualSync() {
        if (_uiState.value.isRunningManualSync) return
        _uiState.value = _uiState.value.copy(isRunningManualSync = true, manualSyncReport = null, manualSyncCrash = null)
        viewModelScope.launch {
            val result = runCatching { appUsageSyncer.sync() }
            _uiState.value = _uiState.value.copy(
                isRunningManualSync = false,
                manualSyncReport = result.getOrNull(),
                manualSyncCrash = result.exceptionOrNull()?.let { it.message ?: it::class.simpleName ?: "Unknown error" },
            )
        }
    }
}

/** The most recently touched entry's state, human-readable -- "never enqueued" if WorkManager has no record of this unique work name at all. */
private fun List<WorkInfo>.describeLatest(): String {
    val info = maxByOrNull { it.generation } ?: return "never enqueued"
    val attempt = if (info.runAttemptCount > 0) " (attempt ${info.runAttemptCount})" else ""
    return "${info.state.name}$attempt"
}
