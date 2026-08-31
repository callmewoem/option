package com.habitsfirst.androidclone.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.habitsfirst.androidclone.domain.model.HabitKind

/**
 * The color signal that tells Gating/Tracked/Antihabit apart at a glance, so kind
 * doesn't have to be read off a label every time -- used as the accent bar on
 * [HabitCard] everywhere it appears (Home, Habits).
 */
@Composable
fun HabitKind.accentColor(): Color = when (this) {
    HabitKind.GATING -> MaterialTheme.colorScheme.primary
    HabitKind.TRACKED -> MaterialTheme.colorScheme.secondary
    HabitKind.ANTIHABIT -> MaterialTheme.colorScheme.error
}
