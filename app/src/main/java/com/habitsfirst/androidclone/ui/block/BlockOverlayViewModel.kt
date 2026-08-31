package com.habitsfirst.androidclone.ui.block

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockUiState(
    val blockedAppLabel: String = "",
    val incompleteHabits: List<HabitProgress> = emptyList(),
    val allHabitsComplete: Boolean = false,
    val isBedtime: Boolean = false,
    val graceTokenCount: Int = 0,
    val graceRedeemed: Boolean = false,
)

@HiltViewModel
class BlockOverlayViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val lootboxRepository: LootboxRepository,
    installedAppsProvider: InstalledAppsProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val blockedPackageName: String =
        savedStateHandle[BlockOverlayActivity.EXTRA_PACKAGE_NAME] ?: ""
    private val isBedtime: Boolean = savedStateHandle[BlockOverlayActivity.EXTRA_IS_BEDTIME] ?: false

    private val appLabel = MutableStateFlow(blockedPackageName)
    private val graceRedeemed = MutableStateFlow(false)

    init {
        if (blockedPackageName.isNotBlank()) {
            appLabel.value = installedAppsProvider.getAppLabel(blockedPackageName)
        }
    }

    val uiState: StateFlow<BlockUiState> = combine(
        habitRepository.observeTodayProgress(),
        appLabel,
        lootboxRepository.graceTokenCount,
        graceRedeemed,
    ) { progress, label, graceTokens, redeemed ->
        val incomplete = progress.filterNot { it.isCompleted }
        BlockUiState(
            blockedAppLabel = label,
            incompleteHabits = incomplete,
            allHabitsComplete = progress.isNotEmpty() && incomplete.isEmpty(),
            isBedtime = isBedtime,
            graceTokenCount = graceTokens,
            graceRedeemed = redeemed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BlockUiState(blockedAppLabel = blockedPackageName, isBedtime = isBedtime),
    )

    /** Not offered during bedtime -- see [com.habitsfirst.androidclone.service.AppBlockAccessibilityService]. */
    fun onRedeemGraceToken(onUnlocked: () -> Unit) {
        if (isBedtime) return
        viewModelScope.launch {
            if (lootboxRepository.redeemGraceToken()) {
                graceRedeemed.value = true
                onUnlocked()
            }
        }
    }
}
