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

/**
 * The tinted-container counterpart of [accentColor] -- what a completed [HabitCard]
 * fills with, so "done" always reads in the same hue as the kind's own accent bar
 * instead of every kind converging on one generic "success green".
 */
@Composable
fun HabitKind.accentContainerColor(): Color = when (this) {
    HabitKind.GATING -> MaterialTheme.colorScheme.primaryContainer
    HabitKind.TRACKED -> MaterialTheme.colorScheme.secondaryContainer
    HabitKind.ANTIHABIT -> MaterialTheme.colorScheme.errorContainer
}

/** The on-color that reads on top of [accentContainerColor]. */
@Composable
fun HabitKind.onAccentContainerColor(): Color = when (this) {
    HabitKind.GATING -> MaterialTheme.colorScheme.onPrimaryContainer
    HabitKind.TRACKED -> MaterialTheme.colorScheme.onSecondaryContainer
    HabitKind.ANTIHABIT -> MaterialTheme.colorScheme.onErrorContainer
}
