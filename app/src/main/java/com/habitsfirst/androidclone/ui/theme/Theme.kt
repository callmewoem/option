package com.habitsfirst.androidclone.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.habitsfirst.androidclone.domain.model.ThemeVariant

/**
 * Every [ThemeVariant] is the same paper/concrete neutral scale underneath -- only the
 * primary/secondary accent pair changes. Centralizing that shared wiring here (rather
 * than repeating ~15 neutral role assignments per variant, per light/dark) is what
 * makes it possible to fill in the *entire* Material tonal system -- including the
 * surfaceContainer ladder that [androidx.compose.material3.NavigationBar], dialogs,
 * bottom sheets and snackbars pull from by default -- without six-way copy/paste drift.
 * Before this, those roles were left at Material's baseline (blue-gray) neutrals
 * because `lightColorScheme`/`darkColorScheme` default them when not passed explicitly,
 * so a NavigationBar or AlertDialog quietly broke the app's warm concrete/paper look.
 */
private fun lightScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    outline: Color = Concrete40,
) = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    background = Concrete99,
    onBackground = Concrete10,
    surface = Concrete99,
    onSurface = Concrete10,
    surfaceVariant = Concrete95,
    onSurfaceVariant = Concrete30,
    outline = outline,
    outlineVariant = Concrete90,
    error = Error40,
    onError = Concrete99,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    surfaceContainerLowest = Concrete99,
    surfaceContainerLow = Concrete97,
    surfaceContainer = Concrete95,
    surfaceContainerHigh = Concrete93,
    surfaceContainerHighest = Concrete90,
    surfaceDim = Concrete90,
    surfaceBright = Concrete99,
    inverseSurface = Concrete20,
    inverseOnSurface = Concrete95,
)

private fun darkScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    outline: Color = Concrete60,
    onSurfaceVariant: Color = Concrete80,
) = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    background = Concrete10,
    onBackground = Concrete90,
    surface = Concrete10,
    onSurface = Concrete90,
    surfaceVariant = Concrete20,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = Concrete30,
    error = Error80,
    onError = Concrete10,
    errorContainer = Error40,
    onErrorContainer = OnErrorContainerDark,
    surfaceContainerLowest = Concrete10,
    surfaceContainerLow = Concrete15,
    surfaceContainer = Concrete20,
    surfaceContainerHigh = Concrete30,
    surfaceContainerHighest = Concrete40,
    surfaceDim = Concrete10,
    surfaceBright = Concrete40,
    inverseSurface = Concrete90,
    inverseOnSurface = Concrete20,
)

private fun lightSchemeFor(variant: ThemeVariant) = when (variant) {
    ThemeVariant.Moss -> lightScheme(
        primary = Moss40, onPrimary = Concrete99, primaryContainer = Moss90, onPrimaryContainer = Moss30,
        secondary = MossSecondary40, onSecondary = Concrete99,
        secondaryContainer = MossSecondary90, onSecondaryContainer = MossSecondary20,
    )
    ThemeVariant.Rust -> lightScheme(
        primary = Rust40, onPrimary = Concrete99, primaryContainer = Rust90, onPrimaryContainer = Rust30,
        secondary = RustSecondary40, onSecondary = Concrete99,
        secondaryContainer = RustSecondary90, onSecondaryContainer = RustSecondary20,
    )
    ThemeVariant.Concrete -> lightScheme(
        primary = Ash40, onPrimary = Concrete99, primaryContainer = Ash90, onPrimaryContainer = Ash30,
        secondary = AshSecondary40, onSecondary = Concrete99,
        secondaryContainer = AshSecondary90, onSecondaryContainer = AshSecondary20,
    )
    ThemeVariant.Ink -> lightScheme(
        primary = Ink40, onPrimary = Concrete99, primaryContainer = Ink90, onPrimaryContainer = Ink30,
        secondary = InkSecondary40, onSecondary = Concrete99,
        secondaryContainer = InkSecondary90, onSecondaryContainer = InkSecondary20,
        outline = Concrete10, // Ink is the loudest variant -- a near-black border reads intentional, not muddy.
    )
    ThemeVariant.Modern -> lightScheme(
        primary = Slate40, onPrimary = Concrete99, primaryContainer = Slate90, onPrimaryContainer = Slate30,
        secondary = SlateSecondary40, onSecondary = Concrete99,
        secondaryContainer = SlateSecondary90, onSecondaryContainer = SlateSecondary20,
    )
    ThemeVariant.Receipt -> lightScheme(
        primary = Carbon40, onPrimary = Concrete99, primaryContainer = Carbon90, onPrimaryContainer = Carbon30,
        secondary = CarbonSecondary40, onSecondary = Concrete99,
        secondaryContainer = CarbonSecondary90, onSecondaryContainer = CarbonSecondary20,
    )
}

private fun darkSchemeFor(variant: ThemeVariant) = when (variant) {
    ThemeVariant.Moss -> darkScheme(
        primary = Moss80, onPrimary = Moss30, primaryContainer = Moss30, onPrimaryContainer = Moss90,
        secondary = MossSecondary80, onSecondary = MossSecondary20,
        secondaryContainer = MossSecondary20, onSecondaryContainer = MossSecondary90,
    )
    ThemeVariant.Rust -> darkScheme(
        primary = Rust80, onPrimary = Rust30, primaryContainer = Rust30, onPrimaryContainer = Rust90,
        secondary = RustSecondary80, onSecondary = RustSecondary20,
        secondaryContainer = RustSecondary20, onSecondaryContainer = RustSecondary90,
    )
    ThemeVariant.Concrete -> darkScheme(
        primary = Ash80, onPrimary = Ash30, primaryContainer = Ash30, onPrimaryContainer = Ash90,
        secondary = AshSecondary80, onSecondary = AshSecondary20,
        secondaryContainer = AshSecondary20, onSecondaryContainer = AshSecondary90,
    )
    ThemeVariant.Ink -> darkScheme(
        primary = Ink80, onPrimary = Ink30, primaryContainer = Ink30, onPrimaryContainer = Ink90,
        secondary = InkSecondary80, onSecondary = InkSecondary20,
        secondaryContainer = InkSecondary20, onSecondaryContainer = InkSecondary90,
        outline = Concrete70, onSurfaceVariant = Concrete90,
    )
    ThemeVariant.Modern -> darkScheme(
        primary = Slate80, onPrimary = Slate30, primaryContainer = Slate30, onPrimaryContainer = Slate90,
        secondary = SlateSecondary80, onSecondary = SlateSecondary20,
        secondaryContainer = SlateSecondary20, onSecondaryContainer = SlateSecondary90,
    )
    ThemeVariant.Receipt -> darkScheme(
        primary = Carbon80, onPrimary = Carbon30, primaryContainer = Carbon30, onPrimaryContainer = Carbon90,
        secondary = CarbonSecondary80, onSecondary = CarbonSecondary20,
        secondaryContainer = CarbonSecondary20, onSecondaryContainer = CarbonSecondary90,
    )
}

@Composable
fun LockeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    variant: ThemeVariant = ThemeVariant.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkSchemeFor(variant) else lightSchemeFor(variant)

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
        typography = LockeTypography,
        shapes = LockeShapes,
        content = content,
    )
}
