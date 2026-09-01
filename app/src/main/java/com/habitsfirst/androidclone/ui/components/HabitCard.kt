package com.habitsfirst.androidclone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.domain.model.HabitProgress
import com.habitsfirst.androidclone.domain.model.HabitType

@Composable
fun HabitCard(
    progress: HabitProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habit = progress.habit
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (progress.isCompleted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Icon(
                    imageVector = habit.type.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (progress.isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = habit.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = when (habit.type) {
                    HabitType.CUSTOM -> if (progress.isCompleted) "Done" else "Tap to mark done"
                    HabitType.IMAGE_VERIFICATION -> if (progress.isCompleted) "Verified" else "Tap to verify with a photo"
                    else -> "${progress.currentValue} / ${habit.targetValue} ${habit.type.unit}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = if (progress.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (progress.isCompleted) "Completed" else "Not completed",
                tint = if (progress.isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}
