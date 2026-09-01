package com.habitsfirst.androidclone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType

/**
 * Flat + bordered rather than elevated -- brutalism has no drop shadows, only outline.
 * [kind]'s accent bar down the left edge is the app's only "which list is this" signal
 * -- callers shouldn't need a descriptive section subtitle to tell Gating, Tracked and
 * Antihabit apart.
 */
@Composable
fun HabitCard(
    progress: HabitProgress,
    kind: HabitKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habit = progress.habit
    val accent = kind.accentColor()
    // For an antihabit, isCompleted means "a slip was logged" -- bad, not good -- so
    // the usual green-on-done mapping inverts.
    val isSlip = kind == HabitKind.ANTIHABIT && progress.isCompleted
    val isDone = progress.isCompleted && kind != HabitKind.ANTIHABIT

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSlip -> MaterialTheme.colorScheme.errorContainer
                isDone -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (kind == HabitKind.ANTIHABIT) {
                        Icon(
                            imageVector = if (isSlip) Icons.Filled.WarningAmber else Icons.Outlined.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (isSlip) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 4.dp,
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Icon(
                            imageVector = habit.type.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isDone) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = habit.name, style = MaterialTheme.typography.titleMedium)
                    val subtitle = when {
                        kind == HabitKind.ANTIHABIT -> if (isSlip) "Slipped" else null
                        habit.type == HabitType.PHOTO ->
                            if (progress.isCompleted) "Verified" else "Tap to verify with a photo"
                        habit.type == HabitType.TALLY -> null
                        else -> "${progress.currentValue} / ${habit.targetValue} ${habit.type.unit}".trim()
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val trailingIcon = when {
                    isSlip -> Icons.Filled.WarningAmber
                    isDone -> Icons.Filled.CheckCircle
                    else -> Icons.Outlined.Circle
                }
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = if (progress.isCompleted) "Logged" else "Not logged",
                    tint = when {
                        isSlip -> MaterialTheme.colorScheme.error
                        isDone -> accent
                        else -> MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
    }
}
