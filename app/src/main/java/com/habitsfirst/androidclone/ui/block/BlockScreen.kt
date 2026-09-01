package com.habitsfirst.androidclone.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    // Bedtime is a hard curfew, not a habit gate -- never auto-dismiss for it just
    // because today's habits happen to be done.
    LaunchedEffect(state.allHabitsComplete, state.isBedtime) {
        if (state.allHabitsComplete && !state.isBedtime) onAllHabitsComplete()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // BlockOverlayActivity never calls enableEdgeToEdge() itself, but API 35
                // enforces edge-to-edge regardless -- without this, "Open Habits" / "Go
                // home" below render under the system navigation bar and can't be tapped.
                .systemBarsPadding()
                .padding(24.dp),
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (state.isBedtime) Icons.Filled.Bedtime else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (state.isBedtime) {
                    stringResource(R.string.block_bedtime_title)
                } else {
                    stringResource(R.string.block_title, state.blockedAppLabel)
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (state.isBedtime) {
                    stringResource(R.string.block_bedtime_subtitle)
                } else {
                    stringResource(R.string.block_subtitle)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (state.isBedtime) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.incompleteHabits, key = { it.habit.id }) { progress ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
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
        }
    }
}
