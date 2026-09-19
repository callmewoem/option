package com.locke.app.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.locke.app.R
import com.locke.app.domain.model.Todo
import com.locke.app.domain.model.TodoList
import com.locke.app.ui.components.LockeCard
import com.locke.app.ui.navigation.LockeBottomBar
import com.locke.app.util.DateProvider

/**
 * Deliberately the plainest screen in the app: no locking power, nothing here gates
 * anything (design spec §9) -- just today and tomorrow's one-off tasks, checked off by
 * hand. Partitioning into user-defined lists (the filter chip row below) is the one
 * piece of organization on top of that -- purely a display grouping, never a gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    navController: NavController,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newTodoText by remember { mutableStateOf("") }
    var newTodoDueTomorrow by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = { LockeBottomBar(navController) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.todos_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "No locking power -- nothing here gates an app or a habit. Just what to get to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                ListFilterRow(
                    lists = state.lists,
                    selectedListId = state.selectedListId,
                    onSelect = viewModel::onSelectListFilter,
                    onDeleteSelected = { list -> viewModel.onDeleteList(list) },
                    onAddList = { showNewListDialog = true },
                )
            }

            if (state.visibleTodos.isEmpty()) {
                item {
                    Text(
                        "Nothing on the list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(state.visibleTodos, key = { it.id }) { todo ->
                    TodoRow(
                        todo = todo,
                        lists = state.lists,
                        onToggle = { viewModel.onToggleDone(todo) },
                        onDelete = { viewModel.onDelete(todo) },
                        onAssignList = { listId -> viewModel.onAssignList(todo, listId) },
                    )
                }
            }

            // Adding a new todo is the least-used action on this screen once a few are
            // already listed -- keeping it below the list instead of pinned above it means
            // the list itself doesn't reflow downward every time this screen opens.
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        label = { Text(stringResource(R.string.todos_add_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.onAddTodo(newTodoText, newTodoDueTomorrow)
                            newTodoText = ""
                            newTodoDueTomorrow = false
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !newTodoDueTomorrow,
                        onClick = { newTodoDueTomorrow = false },
                        label = { Text("Today") },
                    )
                    FilterChip(
                        selected = newTodoDueTomorrow,
                        onClick = { newTodoDueTomorrow = true },
                        label = { Text("Tomorrow") },
                    )
                }
                if (state.selectedListId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val listName = state.lists.firstOrNull { it.id == state.selectedListId }?.name
                    Text(
                        text = "Adds to \"$listName\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showNewListDialog) {
        NewListDialog(
            onDismiss = { showNewListDialog = false },
            onConfirm = { name ->
                viewModel.onCreateList(name)
                showNewListDialog = false
            },
        )
    }
}

/**
 * "All" plus every user-defined [TodoList], Habitica tag-filter style -- tapping one
 * narrows [TodoScreen]'s list (and becomes the default list a new todo is added into);
 * tapping the already-selected one deselects back to "All". The selected list's own
 * chip carries a small delete affordance, since there's no other list-management screen
 * -- deleting only un-assigns its todos (see [com.locke.app.data.repository.TodoRepository.deleteList]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListFilterRow(
    lists: List<TodoList>,
    selectedListId: Long?,
    onSelect: (Long?) -> Unit,
    onDeleteSelected: (TodoList) -> Unit,
    onAddList: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedListId == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        lists.forEach { list ->
            val selected = selectedListId == list.id
            FilterChip(
                selected = selected,
                onClick = { onSelect(if (selected) null else list.id) },
                label = { Text(list.name) },
            )
            // A separate tap target from the chip's own onClick (which toggles the
            // filter) -- only shown once the list is selected, so deleting it is never
            // one accidental tap away.
            if (selected) {
                IconButton(
                    onClick = { onDeleteSelected(list) },
                    modifier = Modifier.height(32.dp).width(32.dp),
                ) {
                    Icon(Icons.Filled.LabelOff, contentDescription = "Delete \"${list.name}\"", modifier = Modifier.height(16.dp))
                }
            }
        }
        FilterChip(
            selected = false,
            onClick = onAddList,
            label = { Text("+ List") },
        )
    }
}

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New list") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. \"Work\"") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun TodoRow(
    todo: Todo,
    lists: List<TodoList>,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAssignList: (Long?) -> Unit,
) {
    var showListMenu by remember { mutableStateOf(false) }
    val assignedList = lists.firstOrNull { it.id == todo.listId }

    LockeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                    color = if (todo.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val tomorrowLabel = if (!DateProvider.isToday(todo.date)) "Tomorrow" else null
                val subtitle = listOfNotNull(tomorrowLabel, assignedList?.name).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { showListMenu = true }) {
                    Icon(Icons.Filled.Label, contentDescription = "Assign to list")
                }
                DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("No list") },
                        onClick = { onAssignList(null); showListMenu = false },
                    )
                    lists.forEach { list ->
                        DropdownMenuItem(
                            text = { Text(list.name) },
                            onClick = { onAssignList(list.id); showListMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.todos_delete))
            }
        }
    }
}
