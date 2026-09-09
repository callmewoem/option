package com.habitsfirst.androidclone.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.habitsfirst.androidclone.domain.model.HabitType

fun HabitType.icon(): ImageVector = when (this) {
    HabitType.TIMED_MINUTES -> Icons.Filled.Timer
    HabitType.APP_USAGE_MINUTES -> Icons.Filled.PhoneAndroid
    HabitType.PHOTO -> Icons.Filled.CameraAlt
    HabitType.TALLY -> Icons.Filled.CheckCircle
    HabitType.STEPS -> Icons.Filled.DirectionsWalk
    HabitType.WORKOUT_MINUTES -> Icons.Filled.FitnessCenter
    HabitType.SLEEP_HOURS -> Icons.Filled.Bedtime
    HabitType.VISIT_LOCATION -> Icons.Filled.Place
    HabitType.GITHUB_CONTRIBUTION -> Icons.Filled.Hub
    HabitType.WAKATIME_CODING_MINUTES -> Icons.Filled.Code
    HabitType.TAG_SCAN -> Icons.Filled.QrCodeScanner
}

fun HabitType.label(): String = when (this) {
    HabitType.TIMED_MINUTES -> "Timed"
    HabitType.APP_USAGE_MINUTES -> "Use an app"
    HabitType.PHOTO -> "Photo"
    HabitType.TALLY -> "Tally"
    HabitType.STEPS -> "Steps"
    HabitType.WORKOUT_MINUTES -> "Workout"
    HabitType.SLEEP_HOURS -> "Sleep"
    HabitType.VISIT_LOCATION -> "Visit a place"
    HabitType.GITHUB_CONTRIBUTION -> "GitHub"
    HabitType.WAKATIME_CODING_MINUTES -> "Code (WakaTime)"
    HabitType.TAG_SCAN -> "Scan a tag"
}
