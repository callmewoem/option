package com.habitsfirst.androidclone.ui.apppicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.domain.model.AppBlockMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_blocked_apps)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp),
                    ) {
                        AppBlockMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.appBlockMode == mode,
                                onClick = { viewModel.onModeChanged(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppBlockMode.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        if (mode == AppBlockMode.BLACKLIST) {
                                            R.string.app_block_mode_blacklist
                                        } else {
                                            R.string.app_block_mode_whitelist
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = stringResource(
                            if (state.appBlockMode == AppBlockMode.BLACKLIST) {
                                R.string.app_block_mode_blacklist_description
                            } else {
                                R.string.app_block_mode_whitelist_description
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        label = { Text("Search apps") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        singleLine = true,
                    )
                }
                item {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        AppSortMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.sortMode == mode,
                                onClick = { viewModel.onSortModeChanged(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppSortMode.entries.size),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
                items(state.filteredApps, key = { it.packageName }) { app ->
                    val isSelected = app.packageName in state.selectedPackageNames
                    val usageMinutes = state.usageMinutesByPackage[app.packageName] ?: 0
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = {
                            val label = when {
                                state.sortMode == AppSortMode.MOST_USED && usageMinutes > 0 ->
                                    "$usageMinutes min today"
                                state.isRecommended(app) -> "Recommended"
                                app.isSystemApp -> "System app"
                                else -> null
                            }
                            if (label != null) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.isRecommended(app)) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = isSelected,
                                onCheckedChange = { viewModel.onToggleApp(app, it) },
                                enabled = !state.isToggleLockedByHardMode(isSelected),
                            )
                        },
                    )
                }
            }
        }
    }
}
