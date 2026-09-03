package com.habitsfirst.androidclone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.domain.model.LootboxReward

private fun LootboxReward.title(): String = when (this) {
    LootboxReward.GracePeriod -> "Grace Token"
    LootboxReward.TaskSkip -> "Task Skip Token — Rare!"
    is LootboxReward.ThemeUnlock -> "New Theme: ${variant.displayName}"
    LootboxReward.GoldStar -> "Gold Star"
}

private fun LootboxReward.description(): String = when (this) {
    LootboxReward.GracePeriod -> "Redeem it from a lock screen for a 1-minute unblock, any time except bedtime."
    LootboxReward.TaskSkip -> "Force-completes one of today's gating habits without doing it. Use it from Settings."
    is LootboxReward.ThemeUnlock -> "Unlocked in Settings → Theme."
    LootboxReward.GoldStar -> "Today's heatmap cell gets a cosmetic gold mark."
}

/** Shown once a day, right when the daily lootbox is won for finishing every gating habit. */
@Composable
fun LootboxRewardDialog(reward: LootboxReward, onDismiss: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lootbox-scale",
    )

    LaunchedEffect(Unit) { revealed = true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily lootbox!") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(reward.title(), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    reward.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Nice") }
        },
    )
}
