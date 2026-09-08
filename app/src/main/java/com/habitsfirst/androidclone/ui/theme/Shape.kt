package com.habitsfirst.androidclone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One radius family, scaled by role rather than applied uniformly -- machined and
 * inset, not soft. Cards read like stamped plates (a small, consistent radius), chips
 * and checkboxes are almost square, and only a sheet's top edge gets a generous curve.
 * Material3 components pull [extraSmall]/[small]/[medium]/[large]/[extraLarge] from
 * [androidx.compose.material3.MaterialTheme.shapes] by default.
 */
val LockeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // chips, checkboxes, thin progress bars
    small = RoundedCornerShape(6.dp), // small controls, badges
    medium = RoundedCornerShape(10.dp), // cards -- the app's default surface
    large = RoundedCornerShape(16.dp), // dialogs, larger cards
    extraLarge = RoundedCornerShape(20.dp), // sheet edges
)

/** A bottom sheet's top-only curve -- used explicitly where Material3 doesn't already default to it. */
val LockeSheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
