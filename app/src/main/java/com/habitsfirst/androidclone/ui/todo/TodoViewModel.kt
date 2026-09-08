package com.habitsfirst.androidclone.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitsfirst.androidclone.data.repository.TodoRepository
import com.habitsfirst.androidclone.domain.model.Todo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodoUiState(val todos: List<Todo> = emptyList())

/**
 * Deliberately the plainest screen in the app -- no locking power, nothing here gates
 * anything (design spec §9). Just today and tomorrow's one-off tasks.
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
) : ViewModel() {

    val uiState: StateFlow<TodoUiState> = todoRepository.observeUpcoming()
        .map { TodoUiState(todos = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoUiState())

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
