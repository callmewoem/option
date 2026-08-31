package com.habitsfirst.androidclone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Forest40,
    onPrimary = Neutral99,
    primaryContainer = Forest90,
    onPrimaryContainer = Forest10,
    secondary = Amber40,
    onSecondary = Neutral99,
    secondaryContainer = Amber80,
    onSecondaryContainer = Neutral10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Forest95,
    onSurfaceVariant = Forest30,
    error = Error40,
    outline = Forest50,
)

private val DarkColors = darkColorScheme(
    primary = Forest80,
    onPrimary = Forest10,
    primaryContainer = Forest30,
    onPrimaryContainer = Forest90,
    secondary = Amber80,
    onSecondary = Neutral10,
    secondaryContainer = Amber40,
    onSecondaryContainer = Neutral95,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Forest20,
    onSurfaceVariant = Forest90,
    error = Error80,
    outline = Forest60,
)

@Composable
fun HabitsFirstTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HabitsFirstTypography,
        content = content,
    )
}
