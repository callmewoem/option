package com.habitsfirst.androidclone.ui.urlblock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.UrlBlockRepository
import com.habitsfirst.androidclone.domain.model.BlockMode
import com.habitsfirst.androidclone.domain.model.UrlBlockList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UrlBlockUiState(
    val lists: List<UrlBlockList> = emptyList(),
    /** Hard mode: an enabled list can't be turned off or loosened from PERMANENT to GATED, and a custom list can't be deleted or lose a domain. */
    val isHardModeEnabled: Boolean = false,
) {
    val premadeLists: List<UrlBlockList> get() = lists.filter { it.source.isPremade }
    val customLists: List<UrlBlockList> get() = lists.filterNot { it.source.isPremade }
}

@HiltViewModel
class UrlBlockViewModel @Inject constructor(
    private val urlBlockRepository: UrlBlockRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<UrlBlockUiState> = combine(
        urlBlockRepository.observeBlockLists(),
        preferencesRepository.isHardModeEnabled,
        ::UrlBlockUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UrlBlockUiState())

    fun domainsForList(listId: String): Flow<List<String>> = urlBlockRepository.observeCustomListDomains(listId)

    fun onListEnabledToggled(list: UrlBlockList, enabled: Boolean) {
        if (!enabled && uiState.value.isHardModeEnabled) return
        viewModelScope.launch { urlBlockRepository.setListEnabled(list.id, enabled) }
    }

    fun onBlockModeChanged(list: UrlBlockList, mode: BlockMode) {
        val loosening = list.blockMode == BlockMode.PERMANENT && mode == BlockMode.GATED
        if (loosening && uiState.value.isHardModeEnabled) return
        viewModelScope.launch { urlBlockRepository.setListBlockMode(list.id, mode) }
    }

    fun onCreateCustomList(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(urlBlockRepository.createCustomList(name)) }
    }

    fun onRenameCustomList(listId: String, name: String) {
        viewModelScope.launch { urlBlockRepository.renameCustomList(listId, name) }
    }

    fun onDeleteCustomList(listId: String) {
        if (uiState.value.isHardModeEnabled) return
        viewModelScope.launch { urlBlockRepository.deleteCustomList(listId) }
    }

    fun onAddDomain(listId: String, domain: String) {
        viewModelScope.launch { urlBlockRepository.addDomain(listId, domain) }
    }

    fun onRemoveDomain(listId: String, domain: String) {
        if (uiState.value.isHardModeEnabled) return
        viewModelScope.launch { urlBlockRepository.removeDomain(listId, domain) }
    }
}
