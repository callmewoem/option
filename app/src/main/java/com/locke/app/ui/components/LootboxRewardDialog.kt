package com.locke.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
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
import com.locke.app.domain.model.LootboxReward
import com.locke.app.ui.theme.LockeColor

private fun LootboxReward.title(): String = when (this) {
    LootboxReward.GracePeriod -> "Grace token"
    LootboxReward.TaskSkip -> "Task-skip token"
    is LootboxReward.ThemeUnlock -> "Cosmetic: ${variant.displayName}"
    LootboxReward.GoldStar -> "Gold star"
}

private fun LootboxReward.description(): String = when (this) {
    LootboxReward.GracePeriod -> "Redeem it from a lock screen for a 1-minute unblock, any time except bedtime."
    LootboxReward.TaskSkip -> "Force-completes one of today's gating habits without doing it. Use it from Settings."
    is LootboxReward.ThemeUnlock -> "Kept in Settings -- the palette itself never changes, this is a collected marker only."
    LootboxReward.GoldStar -> "A cosmetic mark on today's heatmap cell."
}

/**
 * Shown once a day, right when the daily lootbox is won for finishing every gating
 * habit -- brass, and the app's one deliberate moment of motion (design spec §4, §9).
 * Everywhere else in the app stays still; this is the single exception, and only
 * because it's the one thing actually earned.
 */
@Composable
fun LootboxRewardDialog(reward: LootboxReward, onDismiss: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lootbox-scale",
    )

    LaunchedEffect(Unit) { revealed = true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily lootbox") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(scale)
                        .background(LockeColor.Brass.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = LockeColor.Brass,
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
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
