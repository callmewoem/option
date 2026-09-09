package com.habitsfirst.androidclone.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.TodoRepository
import com.habitsfirst.androidclone.domain.model.Todo
import com.habitsfirst.androidclone.util.DateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything shown by the "you didn't do these yesterday" dialog -- every still-undone
 * todo carried in from before today (see [TodoRepository.getOverdueTodos]), plus which
 * of them are currently checked to be kept. Pre-selects all of them so the default
 * action ([TodoViewModel.onConfirmOverduePrompt]) is "keep everything".
 */
data class OverduePromptState(val todos: List<Todo>, val selectedIds: Set<Long>)

data class TodoUiState(val todos: List<Todo> = emptyList(), val overduePrompt: OverduePromptState? = null)

/**
 * Deliberately the plainest screen in the app -- no locking power, nothing here gates
 * anything (design spec §9). Just today and tomorrow's one-off tasks.
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val overduePrompt = MutableStateFlow<OverduePromptState?>(null)

    val uiState: StateFlow<TodoUiState> = combine(todoRepository.observeUpcoming(), overduePrompt) { todos, prompt ->
        TodoUiState(todos = todos, overduePrompt = prompt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoUiState())

    init {
        viewModelScope.launch { checkForOverdueTodos() }
    }

    /**
     * Once per calendar day (tracked via [PreferencesRepository.lastOverdueTodoPromptDate],
     * the same pattern [com.habitsfirst.androidclone.service.MorningReminderWorker] uses
     * for its own once-a-day guard), surfaces yesterday-or-earlier's still-undone todos so
     * the user picks which ones to keep for today instead of them silently reappearing --
     * or silently disappearing.
     */
    private suspend fun checkForOverdueTodos() {
        val today = DateProvider.todayString()
        if (preferencesRepository.lastOverdueTodoPromptDate.first() == today) return

        val overdue = todoRepository.getOverdueTodos(today)
        if (overdue.isEmpty()) {
            preferencesRepository.setLastOverdueTodoPromptDate(today)
            return
        }
        overduePrompt.value = OverduePromptState(todos = overdue, selectedIds = overdue.map { it.id }.toSet())
    }

    fun onToggleOverdueSelection(todoId: Long) {
        overduePrompt.update { state ->
            state?.copy(
                selectedIds = if (todoId in state.selectedIds) state.selectedIds - todoId else state.selectedIds + todoId,
            )
        }
    }

    /** Carries every currently-checked todo over to today; anything left unchecked is simply left where it was. */
    fun onConfirmOverduePrompt() {
        val selectedIds = overduePrompt.value?.selectedIds?.toList() ?: return
        viewModelScope.launch {
            todoRepository.carryOverToToday(selectedIds)
            preferencesRepository.setLastOverdueTodoPromptDate(DateProvider.todayString())
            overduePrompt.value = null
        }
    }

    /** Closes the dialog without carrying anything over -- still marks today as prompted, so it won't nag again until tomorrow. */
    fun onDismissOverduePrompt() {
        viewModelScope.launch {
            preferencesRepository.setLastOverdueTodoPromptDate(DateProvider.todayString())
            overduePrompt.value = null
        }
    }

    fun onAddTodo(title: String, dueTomorrow: Boolean = false) {
        if (title.isBlank()) return
        viewModelScope.launch { todoRepository.addTodo(title, dueTomorrow = dueTomorrow) }
    }

    fun onToggleDone(todo: Todo) {
        viewModelScope.launch { todoRepository.setDone(todo, !todo.isDone) }
    }

    fun onDelete(todo: Todo) {
        viewModelScope.launch { todoRepository.delete(todo) }
    }
}
