package com.habitsfirst.androidclone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Softened brutalism: a deliberate, moderate corner scale rather than fully rounded
 * "pill" Material You shapes on one end, or the old flat 0dp square corners on the
 * other. Corners stay tight enough that the thick [androidx.compose.foundation.BorderStroke]
 * outline used on cards throughout the app still reads as a stamped, poured-concrete
 * edge -- just no longer a literal knife-edge -- which is what actually reads as
 * "dated" rather than "raw" to a modern eye. Material3 components pull these from
 * [androidx.compose.material3.MaterialTheme.shapes] by default, so this one definition
 * updates every card, button, chip, text field, sheet and dialog in the app.
 */
val LockeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
