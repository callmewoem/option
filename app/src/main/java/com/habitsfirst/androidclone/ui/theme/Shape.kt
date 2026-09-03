package com.habitsfirst.androidclone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A generous, friendly corner scale -- warmer than the old flat 0dp brutalist corners,
 * and rounder than the tightly-stamped 6-28dp scale that came after it. Material3
 * components pull these from [androidx.compose.material3.MaterialTheme.shapes] by
 * default, so this one definition updates every card, button, chip, text field, sheet
 * and dialog in the app.
 */
val LockeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
