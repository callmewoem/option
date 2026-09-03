package com.habitsfirst.androidclone.ui.block

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R

@Composable
fun BlockScreen(
    onTakeBreak: () -> Unit,
    onOpenHabitsFirst: () -> Unit,
    onAllHabitsComplete: () -> Unit,
    onGraceRedeemed: () -> Unit,
    viewModel: BlockOverlayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Bedtime is a hard curfew and a permanent block never lifts -- neither auto-
    // dismisses just because today's habits happen to be done.
    LaunchedEffect(state.allHabitsComplete, state.isBedtime, state.isPermanent) {
        if (state.allHabitsComplete && !state.isBedtime && !state.isPermanent) onAllHabitsComplete()
    }

    // A locked app's cover is the app's whole reason to exist -- it earns a bolder
    // treatment than any other screen: a full-bleed accent-tinted field behind the
    // badge and headline, rather than another neutral card on a plain background.
    val fieldColor = when {
        state.isBedtime -> MaterialTheme.colorScheme.secondaryContainer
        state.isPermanent -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val onFieldColor = when {
        state.isBedtime -> MaterialTheme.colorScheme.onSecondaryContainer
        state.isPermanent -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(fieldColor)
                    // BlockOverlayActivity never calls enableEdgeToEdge() itself, but API 35
                    // enforces edge-to-edge regardless -- without this, the status bar area
                    // renders under system chrome.
                    .systemBarsPadding()
                    .padding(24.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(onFieldColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            state.isBedtime -> Icons.Filled.Bedtime
                            state.isPermanent -> Icons.Filled.Block
                            else -> Icons.Filled.Lock
                        },
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = onFieldColor,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = when {
                        state.isBedtime -> stringResource(R.string.block_bedtime_title)
                        state.isPermanent -> stringResource(R.string.block_permanent_title, state.blockedLabel)
                        else -> stringResource(R.string.block_title, state.blockedLabel)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = onFieldColor,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        state.isBedtime -> stringResource(R.string.block_bedtime_subtitle)
                        state.isPermanent -> stringResource(
                            R.string.block_permanent_subtitle,
                            state.listName.orEmpty(),
                        )
                        state.isUrlBlock -> stringResource(R.string.block_url_subtitle)
                        else -> stringResource(R.string.block_subtitle)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = onFieldColor.copy(alpha = 0.85f),
                )
                if (!state.isBedtime && !state.isPermanent && state.incompleteHabits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val count = state.incompleteHabits.size
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(onFieldColor.copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = if (count == 1) "1 habit stands between you and it" else "$count habits stand between you and it",
                            style = MaterialTheme.typography.labelLarge,
                            color = onFieldColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                if (state.isBedtime || state.isPermanent) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.incompleteHabits, key = { it.habit.id }) { progress ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = progress.habit.name,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "${progress.currentValue} / ${progress.habit.targetValue} ${progress.habit.type.unit}"
                                            .trim(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (state.graceTokenCount > 0 && !state.graceRedeemed) {
                        TextButton(
                            onClick = { viewModel.onRedeemGraceToken(onGraceRedeemed) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.block_use_grace_token, state.graceTokenCount))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenHabitsFirst,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(stringResource(R.string.block_open_habits))
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onTakeBreak,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.block_go_home))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
