package com.habitsfirst.androidclone.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.habitsfirst.androidclone.domain.model.HabitType

fun HabitType.icon(): ImageVector = when (this) {
    HabitType.TIMED_MINUTES -> Icons.Filled.Timer
    HabitType.APP_USAGE_MINUTES -> Icons.Filled.PhoneAndroid
    HabitType.PHOTO -> Icons.Filled.CameraAlt
    HabitType.TALLY -> Icons.Filled.CheckCircle
    HabitType.STEPS -> Icons.Filled.DirectionsWalk
}

fun HabitType.label(): String = when (this) {
    HabitType.TIMED_MINUTES -> "Timed"
    HabitType.APP_USAGE_MINUTES -> "Use an app"
    HabitType.PHOTO -> "Photo"
    HabitType.TALLY -> "Tally"
    HabitType.STEPS -> "Steps"
}
