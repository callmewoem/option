package com.habitsfirst.androidclone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Brutalist shapes: square corners everywhere. Material3 components pull these from
 * [androidx.compose.material3.MaterialTheme.shapes] by default, so this one definition
 * flattens every card, button, text field, sheet and dialog in the app without having
 * to touch each call site.
 */
val LockeShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)
