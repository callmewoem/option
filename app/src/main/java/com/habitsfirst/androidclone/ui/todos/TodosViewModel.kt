package com.habitsfirst.androidclone.ui.todos

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

data class TodosUiState(
    val todos: List<Todo> = emptyList(),
) {
    val pending: List<Todo> get() = todos.filterNot { it.isDone }
    val done: List<Todo> get() = todos.filter { it.isDone }
}

@HiltViewModel
class TodosViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
) : ViewModel() {

    val uiState: StateFlow<TodosUiState> = todoRepository.observeForDate()
        .map { TodosUiState(todos = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodosUiState())

    fun onAddTodo(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { todoRepository.addTodo(title) }
    }

    fun onToggleDone(todo: Todo) {
        viewModelScope.launch { todoRepository.setDone(todo.id, !todo.isDone) }
    }

    fun onDelete(todo: Todo) {
        viewModelScope.launch { todoRepository.delete(todo) }
    }
}
