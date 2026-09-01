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
    val blockedLabel: String = "",
    val isUrlBlock: Boolean = false,
    val listName: String? = null,
    val isPermanent: Boolean = false,
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

    private val target: String = savedStateHandle[BlockOverlayActivity.EXTRA_TARGET] ?: ""
    private val isUrlBlock: Boolean = savedStateHandle[BlockOverlayActivity.EXTRA_IS_URL_BLOCK] ?: false
    private val listName: String? = savedStateHandle[BlockOverlayActivity.EXTRA_LIST_NAME]
    private val isPermanent: Boolean = savedStateHandle[BlockOverlayActivity.EXTRA_IS_PERMANENT] ?: false
    private val isBedtime: Boolean = savedStateHandle[BlockOverlayActivity.EXTRA_IS_BEDTIME] ?: false

    // A URL block's target is already the host to display; an app block's is a
    // package name, which needs resolving to its user-facing label.
    private val blockedLabel = MutableStateFlow(target)
    private val graceRedeemed = MutableStateFlow(false)

    init {
        if (!isUrlBlock && target.isNotBlank()) {
            blockedLabel.value = installedAppsProvider.getAppLabel(target)
        }
    }

    val uiState: StateFlow<BlockUiState> = combine(
        habitRepository.observeTodayProgress(),
        blockedLabel,
        lootboxRepository.graceTokenCount,
        graceRedeemed,
    ) { progress, label, graceTokens, redeemed ->
        val incomplete = progress.filterNot { it.isCompleted }
        BlockUiState(
            blockedLabel = label,
            isUrlBlock = isUrlBlock,
            listName = listName,
            isPermanent = isPermanent,
            incompleteHabits = incomplete,
            allHabitsComplete = progress.isNotEmpty() && incomplete.isEmpty(),
            isBedtime = isBedtime,
            graceTokenCount = graceTokens,
            graceRedeemed = redeemed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BlockUiState(
            blockedLabel = target,
            isUrlBlock = isUrlBlock,
            listName = listName,
            isPermanent = isPermanent,
            isBedtime = isBedtime,
        ),
    )

    /** Not offered during bedtime or a permanent block -- see [com.habitsfirst.androidclone.service.AppBlockAccessibilityService]. */
    fun onRedeemGraceToken(onUnlocked: () -> Unit) {
        if (isBedtime || isPermanent) return
        viewModelScope.launch {
            if (lootboxRepository.redeemGraceToken()) {
                graceRedeemed.value = true
                onUnlocked()
            }
        }
    }
}
