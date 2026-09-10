package com.habitsfirst.androidclone.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.ui.theme.LocalLockeSurfaceIsDark
import com.habitsfirst.androidclone.ui.theme.LockeColor

/**
 * The color signal that tells Gating/Tracked/Antihabit apart at a glance -- coded by
 * *consequence*, matching [ConsequenceChip] (design spec §4): gating is verdigris
 * ("gates your apps"), tracked is a plain neutral grey ("just tracked" -- brass is
 * earned-only and never stands in for a merely-tracked habit), antihabit is oxide
 * ("abstinence").
 */
@Composable
fun HabitKind.accentColor(): Color = when (this) {
    HabitKind.GATING -> MaterialTheme.colorScheme.primary
    HabitKind.TRACKED -> if (LocalLockeSurfaceIsDark.current) LockeColor.NeutralOnIron else LockeColor.NeutralOnBone
    HabitKind.ANTIHABIT -> LockeColor.Oxide
}

/** The tinted-container counterpart of [accentColor] -- what a completed [HabitCard] fills with. */
@Composable
fun HabitKind.accentContainerColor(): Color = when (this) {
    HabitKind.GATING -> MaterialTheme.colorScheme.primaryContainer
    HabitKind.TRACKED -> MaterialTheme.colorScheme.surfaceContainerHigh
    HabitKind.ANTIHABIT -> MaterialTheme.colorScheme.errorContainer
}

/** The on-color that reads on top of [accentContainerColor]. */
@Composable
fun HabitKind.onAccentContainerColor(): Color = when (this) {
    HabitKind.GATING -> MaterialTheme.colorScheme.onPrimaryContainer
    HabitKind.TRACKED -> MaterialTheme.colorScheme.onSurfaceVariant
    HabitKind.ANTIHABIT -> MaterialTheme.colorScheme.onErrorContainer
}
