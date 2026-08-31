package com.habitsfirst.androidclone.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.ThemeVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Resolves the user's chosen [ThemeVariant] (a lootbox reward) for the root composables. */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val selectedVariant: StateFlow<ThemeVariant> = preferencesRepository.selectedThemeVariantId
        .map { ThemeVariant.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeVariant.DEFAULT)
}
