package com.habitsfirst.androidclone.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector
import com.habitsfirst.androidclone.domain.model.HabitType

fun HabitType.icon(): ImageVector = when (this) {
    HabitType.STEPS -> Icons.Filled.DirectionsWalk
    HabitType.EXERCISE_MINUTES -> Icons.Filled.FitnessCenter
    HabitType.MEDITATION_MINUTES -> Icons.Filled.SelfImprovement
    HabitType.APP_USAGE_MINUTES -> Icons.Filled.PhoneAndroid
    HabitType.CUSTOM -> Icons.Filled.CheckCircle
    HabitType.IMAGE_VERIFICATION -> Icons.Filled.CameraAlt
}

fun HabitType.label(): String = when (this) {
    HabitType.STEPS -> "Walk steps"
    HabitType.EXERCISE_MINUTES -> "Exercise"
    HabitType.MEDITATION_MINUTES -> "Meditate"
    HabitType.APP_USAGE_MINUTES -> "Use an app"
    HabitType.CUSTOM -> "Custom check-in"
    HabitType.IMAGE_VERIFICATION -> "Photo verification"
}
