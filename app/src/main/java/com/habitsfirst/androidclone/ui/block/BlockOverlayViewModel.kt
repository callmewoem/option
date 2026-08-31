package com.habitsfirst.androidclone.ui.block

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BlockUiState(
    val blockedAppLabel: String = "",
    val incompleteHabits: List<HabitProgress> = emptyList(),
    val allHabitsComplete: Boolean = false,
)

@HiltViewModel
class BlockOverlayViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    installedAppsProvider: InstalledAppsProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val blockedPackageName: String =
        savedStateHandle[BlockOverlayActivity.EXTRA_PACKAGE_NAME] ?: ""

    private val appLabel = MutableStateFlow(blockedPackageName)

    init {
        if (blockedPackageName.isNotBlank()) {
            appLabel.value = installedAppsProvider.getAppLabel(blockedPackageName)
        }
    }

    val uiState: StateFlow<BlockUiState> = combine(
        habitRepository.observeTodayProgress(),
        appLabel,
    ) { progress, label ->
        val incomplete = progress.filterNot { it.isCompleted }
        BlockUiState(
            blockedAppLabel = label,
            incompleteHabits = incomplete,
            allHabitsComplete = progress.isNotEmpty() && incomplete.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BlockUiState(blockedAppLabel = blockedPackageName),
    )
}
