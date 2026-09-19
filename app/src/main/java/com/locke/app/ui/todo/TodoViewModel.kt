package com.locke.app.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locke.app.data.repository.TodoRepository
import com.locke.app.domain.model.Todo
import com.locke.app.domain.model.TodoList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodoUiState(
    val todos: List<Todo> = emptyList(),
    val lists: List<TodoList> = emptyList(),
    /** Which list's chip is selected to filter by -- null means "All". */
    val selectedListId: Long? = null,
) {
    val visibleTodos: List<Todo>
        get() = if (selectedListId == null) todos else todos.filter { it.listId == selectedListId }
}

/**
 * Deliberately the plainest screen in the app -- no locking power, nothing here gates
 * anything (design spec §9). Just today and tomorrow's one-off tasks. The
 * "you didn't do these yesterday" prompt lives one level up, in
 * [com.locke.app.AppViewModel] -- it's tied to the app being opened or
 * resumed, not to this screen being opened, so it belongs above any one tab.
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
) : ViewModel() {

    private val selectedListId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<TodoUiState> = combine(
        todoRepository.observeUpcoming(),
        todoRepository.observeLists(),
        selectedListId,
    ) { todos, lists, selected ->
        TodoUiState(todos = todos, lists = lists, selectedListId = selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoUiState())

    fun onAddTodo(title: String, dueTomorrow: Boolean = false) {
        if (title.isBlank()) return
        viewModelScope.launch { todoRepository.addTodo(title, dueTomorrow = dueTomorrow, listId = selectedListId.value) }
    }

    fun onToggleDone(todo: Todo) {
        viewModelScope.launch { todoRepository.setDone(todo, !todo.isDone) }
    }

    fun onDelete(todo: Todo) {
        viewModelScope.launch { todoRepository.delete(todo) }
    }

    fun onSelectListFilter(listId: Long?) {
        selectedListId.value = listId
    }

    fun onCreateList(name: String) {
        viewModelScope.launch { todoRepository.createList(name) }
    }

    fun onDeleteList(list: TodoList) {
        if (selectedListId.value == list.id) selectedListId.value = null
        viewModelScope.launch { todoRepository.deleteList(list) }
    }

    fun onAssignList(todo: Todo, listId: Long?) {
        viewModelScope.launch { todoRepository.setList(todo, listId) }
    }
}
