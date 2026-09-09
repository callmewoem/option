package com.habitsfirst.androidclone.ui.block

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.ui.components.LockeCard
import com.habitsfirst.androidclone.ui.components.LockeGhostButton
import com.habitsfirst.androidclone.ui.components.LockePrimaryButton
import com.habitsfirst.androidclone.ui.components.LockeQuietButton
import com.habitsfirst.androidclone.ui.theme.LockeColor

/**
 * The single most-seen screen in the app -- the everyday block cover over one locked
 * app or site. Iron/enforcement mode, no mascot, no praise, no nagging (design spec
 * §4, §6.4): a plain statement of what's locked and what unlocks it.
 */
@Composable
fun BlockScreen(
    onTakeBreak: () -> Unit,
    onOpenHabitsFirst: () -> Unit,
    onAllHabitsComplete: () -> Unit,
    onGraceRedeemed: () -> Unit,
    viewModel: BlockOverlayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Curfew is its own screen entirely -- no buttons, pure countdown (design spec
    // §4, §9): a hard digital curfew a person mid-craving could still tap "Take a
    // break" out of isn't a curfew. Branching here, before anything else in this
    // composable runs, keeps that screen from inheriting any of the single-app cover's
    // affordances by accident.
    if (state.isBedtime) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CurfewScreen(bedtimeEnd = state.bedtimeEnd)
        }
        return
    }

    // Same "no buttons, pure countdown" treatment as bedtime, and for the same reason
    // (see LockoutScreen's doc) -- checked before the auto-dismiss effect below too, so
    // habits happening to be complete doesn't end a lockout early.
    if (state.isLockout) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LockoutScreen(lockoutUntilEpochMillis = state.lockoutUntilEpochMillis)
        }
        return
    }

    // A permanent block never lifts, so it never auto-dismisses just because today's
    // habits happen to be done. Nor does a block that's holding despite habits being
    // complete (an active penalty, or limited unblocking's window running out) --
    // otherwise this would fire the instant it's shown, since state.allHabitsComplete
    // is already true in exactly that case.
    LaunchedEffect(state.allHabitsComplete, state.isPermanent, state.habitsCompleteButLocked) {
        if (state.allHabitsComplete && !state.isPermanent && !state.habitsCompleteButLocked) {
            onAllHabitsComplete()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // BlockOverlayActivity never calls enableEdgeToEdge() itself, but API 35
                // enforces edge-to-edge regardless -- without this, the buttons below
                // render under the system navigation bar and can't be tapped.
                .systemBarsPadding()
                .padding(24.dp),
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            val (icon, iconTint, iconOutlined) = if (state.isPermanent) {
                Triple(Icons.Filled.Block, LockeColor.OnIron, true)
            } else {
                Triple(Icons.Filled.Lock, MaterialTheme.colorScheme.primary, false)
            }
            Surface(
                shape = CircleShape,
                color = if (iconOutlined) Color.Transparent else iconTint.copy(alpha = 0.14f),
                border = if (iconOutlined) BorderStroke(1.5.dp, LockeColor.PermanentOutline) else null,
                modifier = Modifier.size(64.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (state.isPermanent) {
                    stringResource(R.string.block_permanent_title, state.blockedLabel)
                } else {
                    stringResource(R.string.block_title, state.blockedLabel)
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    state.isPermanent -> stringResource(
                        R.string.block_permanent_subtitle,
                        state.listName.orEmpty(),
                    )
                    state.habitsCompleteButLocked -> stringResource(R.string.block_habits_complete_but_locked_subtitle)
                    state.isUrlBlock -> stringResource(R.string.block_url_subtitle)
                    else -> stringResource(R.string.block_subtitle)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (state.isPermanent) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.incompleteHabits, key = { it.habit.id }) { progress ->
                        LockeCard(modifier = Modifier.fillMaxWidth()) {
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
                    LockeQuietButton(
                        text = stringResource(R.string.block_use_grace_token, state.graceTokenCount),
                        onClick = { viewModel.onRedeemGraceToken(onGraceRedeemed) },
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = LockeColor.Brass,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LockePrimaryButton(
                text = stringResource(R.string.block_open_habits),
                onClick = onOpenHabitsFirst,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            LockeGhostButton(
                text = stringResource(R.string.block_go_home),
                onClick = onTakeBreak,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
