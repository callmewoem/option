package com.habitsfirst.androidclone.ui.todo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.domain.model.Todo

/**
 * Everything shown by the "you didn't do these yesterday" dialog -- every still-undone
 * todo carried in from before today (see
 * [com.habitsfirst.androidclone.data.repository.TodoRepository.getOverdueTodos]), plus
 * which of them are currently checked to be kept. Pre-selects all of them so the
 * default action is "keep everything".
 */
data class OverduePromptState(val todos: List<Todo>, val selectedIds: Set<Long>)

/**
 * Shown once a day on app launch/resume (see [com.habitsfirst.androidclone.AppViewModel])
 * for todos left undone from before today -- rather than having them silently carry
 * over or silently drop off the list, the user picks which ones still matter. Every
 * row starts checked; confirming carries only the checked ones over to today,
 * dismissing carries none over but won't ask again until the app is next resumed on a
 * later day.
 */
@Composable
fun OverdueTodoDialog(
    prompt: OverduePromptState,
    onToggle: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.todos_overdue_prompt_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.todos_overdue_prompt_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                prompt.todos.forEach { todo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = todo.id in prompt.selectedIds,
                            onCheckedChange = { onToggle(todo.id) },
                        )
                        Text(todo.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.todos_overdue_prompt_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.todos_overdue_prompt_dismiss)) }
        },
    )
}
