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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType

/**
 * Flat + bordered rather than elevated -- brutalism has no drop shadows, only outline.
 * [kind]'s accent color runs down the left edge *and*, once the habit is logged for the
 * day, tints the whole card via [accentContainerColor] -- so "done" always reads in
 * that kind's own hue (a completed antihabit slip stays alarming, not a generic green)
 * instead of every kind converging on one identical "success" look.
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
    val isHighlighted = isSlip || isDone

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) kind.accentContainerColor() else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, if (isHighlighted) accent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (kind == HabitKind.ANTIHABIT) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (isSlip) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isSlip) Icons.Filled.WarningAmber else Icons.Outlined.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSlip) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 4.dp,
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        kind == HabitKind.ANTIHABIT -> if (isSlip) "Slipped today" else "Clean so far"
                        habit.type == HabitType.PHOTO ->
                            if (progress.isCompleted) "Verified" else "Tap to verify with a photo"
                        habit.type == HabitType.TALLY -> null
                        else -> "${progress.currentValue} / ${habit.targetValue} ${habit.type.unit}".trim()
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isHighlighted) {
                                kind.onAccentContainerColor()
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
                    modifier = Modifier.size(22.dp),
                    tint = if (isHighlighted) accent else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
