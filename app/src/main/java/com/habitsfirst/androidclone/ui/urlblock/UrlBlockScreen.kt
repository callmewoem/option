package com.habitsfirst.androidclone.ui.urlblock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.domain.model.BlockMode
import com.habitsfirst.androidclone.domain.model.UrlBlockList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlBlockScreen(
    onBack: () -> Unit,
    viewModel: UrlBlockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingListId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked websites") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SectionHeader("Blanket lists") }
            item {
                Text(
                    "Curated starter lists. \"Unlock via gating\" opens once today's habits are done, " +
                        "just like a blocked app -- \"Perma-blocked\" never opens, no habit or grace token gets past it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(state.premadeLists, key = { it.id }) { list ->
                BlockListRow(
                    list = list,
                    hardModeEnabled = state.isHardModeEnabled,
                    onToggleEnabled = { enabled -> viewModel.onListEnabledToggled(list, enabled) },
                    onModeChanged = { mode -> viewModel.onBlockModeChanged(list, mode) },
                )
            }

            item { SectionHeader("Custom lists") }
            items(state.customLists, key = { it.id }) { list ->
                BlockListRow(
                    list = list,
                    hardModeEnabled = state.isHardModeEnabled,
                    onToggleEnabled = { enabled -> viewModel.onListEnabledToggled(list, enabled) },
                    onModeChanged = { mode -> viewModel.onBlockModeChanged(list, mode) },
                    onClick = { editingListId = list.id },
                    onDelete = { viewModel.onDeleteCustomList(list.id) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Add custom list") },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateDialog = true },
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                viewModel.onCreateCustomList(name) { newId -> editingListId = newId }
            },
        )
    }

    val editingList = state.customLists.find { it.id == editingListId }
    if (editingList != null) {
        val domains by viewModel.domainsForList(editingList.id).collectAsStateWithLifecycle(initialValue = emptyList())
        DomainEditorDialog(
            list = editingList,
            hardModeEnabled = state.isHardModeEnabled,
            domains = domains,
            onAddDomain = { domain -> viewModel.onAddDomain(editingList.id, domain) },
            onRemoveDomain = { domain -> viewModel.onRemoveDomain(editingList.id, domain) },
            onDismiss = { editingListId = null },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockListRow(
    list: UrlBlockList,
    hardModeEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onModeChanged: (BlockMode) -> Unit,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(list.name) },
            supportingContent = {
                Text(if (list.domainCount == 1) "1 domain" else "${list.domainCount} domains")
            },
            trailingContent = {
                Row {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, enabled = !hardModeEnabled) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete list")
                        }
                    }
                    Switch(
                        checked = list.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        // Hard mode: an enabled list can be added to but never switched off.
                        enabled = !(list.isEnabled && hardModeEnabled),
                    )
                }
            },
            modifier = if (onClick != null) {
                Modifier.fillMaxWidth().clickable(onClick = onClick)
            } else {
                Modifier.fillMaxWidth()
            },
        )
        if (list.isEnabled) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            ) {
                BlockMode.entries.forEachIndexed { index, mode ->
                    // Hard mode: a list can only get stricter (gated -> permanent), never looser.
                    val locked = hardModeEnabled && list.blockMode == BlockMode.PERMANENT && mode == BlockMode.GATED
                    SegmentedButton(
                        selected = list.blockMode == mode,
                        onClick = { onModeChanged(mode) },
                        enabled = !locked,
                        shape = SegmentedButtonDefaults.itemShape(index, BlockMode.entries.size),
                    ) {
                        Text(if (mode == BlockMode.GATED) "Unlock via gating" else "Perma-blocked")
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun CreateListDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New custom list") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("List name") },
                placeholder = { Text("e.g. News sites") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DomainEditorDialog(
    list: UrlBlockList,
    hardModeEnabled: Boolean,
    domains: List<String>,
    onAddDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newDomain by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(list.name) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        label = { Text("Domain") },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (newDomain.isNotBlank()) {
                                onAddDomain(newDomain)
                                newDomain = ""
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add domain")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (domains.isEmpty()) {
                    Text(
                        "No domains yet -- add one above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(domains, key = { it }) { domain ->
                            ListItem(
                                headlineContent = { Text(domain) },
                                trailingContent = {
                                    IconButton(onClick = { onRemoveDomain(domain) }, enabled = !hardModeEnabled) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove domain")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}
