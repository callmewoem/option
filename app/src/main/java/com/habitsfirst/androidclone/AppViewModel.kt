package com.habitsfirst.androidclone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.TodoRepository
import com.habitsfirst.androidclone.domain.model.ThemeMode
import com.habitsfirst.androidclone.ui.todo.OverduePromptState
import com.habitsfirst.androidclone.util.DateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Root-level, [MainActivity]-scoped ViewModel (requested via `hiltViewModel()` outside
 * the nav graph, so it lives above any one screen) for state that's about the app being
 * opened, not about any particular tab. Right now that's the "you didn't do these
 * yesterday" todo prompt (see [onAppResumed]) and the user's [ThemeMode] preference, read
 * here so [MainActivity] can apply it to the whole app-mode nav host in one place.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _overduePrompt = MutableStateFlow<OverduePromptState?>(null)
    val overduePrompt: StateFlow<OverduePromptState?> = _overduePrompt.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DEFAULT)

    /**
     * Call on every `ON_RESUME` (cold launch included). Compares today's date against
     * [PreferencesRepository.lastAppResumeDate] -- the date the app was last brought to
     * the foreground -- and only surfaces overdue todos when that's an *earlier* day
     * than today, i.e. this resume is the first one since the calendar day changed.
     * Switching tabs or briefly hopping to another app and back doesn't retrigger it,
     * since [PreferencesRepository.lastAppResumeDate] is updated to today below on every
     * call, including a no-op one.
     */
    fun onAppResumed() {
        viewModelScope.launch {
            val today = DateProvider.todayString()
            val lastResumeDate = preferencesRepository.lastAppResumeDate.first()
            if (lastResumeDate != null && lastResumeDate != today) {
                val overdue = todoRepository.getOverdueTodos(today)
                if (overdue.isNotEmpty()) {
                    _overduePrompt.value = OverduePromptState(todos = overdue, selectedIds = overdue.map { it.id }.toSet())
                }
            }
            preferencesRepository.setLastAppResumeDate(today)
        }
    }

    fun onToggleOverdueSelection(todoId: Long) {
        _overduePrompt.update { state ->
            state?.copy(
                selectedIds = if (todoId in state.selectedIds) state.selectedIds - todoId else state.selectedIds + todoId,
            )
        }
    }

    /** Carries every currently-checked todo over to today; anything left unchecked is simply left where it was. */
    fun onConfirmOverduePrompt() {
        val selectedIds = _overduePrompt.value?.selectedIds?.toList() ?: return
        viewModelScope.launch {
            todoRepository.carryOverToToday(selectedIds)
            _overduePrompt.value = null
        }
    }

    /** Closes the dialog without carrying anything over. Nothing to persist here -- [onAppResumed] already won't ask again until the app's next resume on a later day. */
    fun onDismissOverduePrompt() {
        _overduePrompt.value = null
    }
}
