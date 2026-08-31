package com.habitsfirst.androidclone.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * A lootbox-unlockable accent pair. Shapes and type never change between variants --
 * only the primary/secondary signal color does, so every variant still reads as the
 * same eco-brutalist app. [Moss] is the only variant unlocked by default.
 */
enum class ThemeVariant(val displayName: String) {
    Moss("Moss"),
    Rust("Rust"),
    Concrete("Concrete"),
    Ink("Ink");

    companion object {
        val DEFAULT = Moss

        fun fromId(id: String?): ThemeVariant = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}

private fun lightSchemeFor(variant: ThemeVariant) = when (variant) {
    ThemeVariant.Moss -> lightColorScheme(
        primary = Moss40, onPrimary = Concrete99, primaryContainer = Moss90, onPrimaryContainer = Moss30,
        secondary = MossSecondary40, onSecondary = Concrete99,
        secondaryContainer = MossSecondary90, onSecondaryContainer = MossSecondary20,
        background = Concrete99, onBackground = Concrete10, surface = Concrete99, onSurface = Concrete10,
        surfaceVariant = Concrete95, onSurfaceVariant = Concrete30, error = Error40, outline = Concrete40,
    )
    ThemeVariant.Rust -> lightColorScheme(
        primary = Rust40, onPrimary = Concrete99, primaryContainer = Rust90, onPrimaryContainer = Rust30,
        secondary = RustSecondary40, onSecondary = Concrete99,
        secondaryContainer = RustSecondary90, onSecondaryContainer = RustSecondary20,
        background = Concrete99, onBackground = Concrete10, surface = Concrete99, onSurface = Concrete10,
        surfaceVariant = Concrete95, onSurfaceVariant = Concrete30, error = Error40, outline = Concrete40,
    )
    ThemeVariant.Concrete -> lightColorScheme(
        primary = Ash40, onPrimary = Concrete99, primaryContainer = Ash90, onPrimaryContainer = Ash30,
        secondary = AshSecondary40, onSecondary = Concrete99,
        secondaryContainer = AshSecondary90, onSecondaryContainer = AshSecondary20,
        background = Concrete99, onBackground = Concrete10, surface = Concrete99, onSurface = Concrete10,
        surfaceVariant = Concrete95, onSurfaceVariant = Concrete30, error = Error40, outline = Concrete40,
    )
    ThemeVariant.Ink -> lightColorScheme(
        primary = Ink40, onPrimary = Concrete99, primaryContainer = Ink90, onPrimaryContainer = Ink30,
        secondary = InkSecondary40, onSecondary = Concrete99,
        secondaryContainer = InkSecondary90, onSecondaryContainer = InkSecondary20,
        background = Concrete99, onBackground = Concrete10, surface = Concrete99, onSurface = Concrete10,
        surfaceVariant = Concrete95, onSurfaceVariant = Concrete30, error = Error40, outline = Concrete10,
    )
}

private fun darkSchemeFor(variant: ThemeVariant) = when (variant) {
    ThemeVariant.Moss -> darkColorScheme(
        primary = Moss80, onPrimary = Moss30, primaryContainer = Moss30, onPrimaryContainer = Moss90,
        secondary = MossSecondary80, onSecondary = MossSecondary20,
        secondaryContainer = MossSecondary20, onSecondaryContainer = MossSecondary90,
        background = Concrete10, onBackground = Concrete90, surface = Concrete10, onSurface = Concrete90,
        surfaceVariant = Concrete20, onSurfaceVariant = Concrete80, error = Error80, outline = Concrete60,
    )
    ThemeVariant.Rust -> darkColorScheme(
        primary = Rust80, onPrimary = Rust30, primaryContainer = Rust30, onPrimaryContainer = Rust90,
        secondary = RustSecondary80, onSecondary = RustSecondary20,
        secondaryContainer = RustSecondary20, onSecondaryContainer = RustSecondary90,
        background = Concrete10, onBackground = Concrete90, surface = Concrete10, onSurface = Concrete90,
        surfaceVariant = Concrete20, onSurfaceVariant = Concrete80, error = Error80, outline = Concrete60,
    )
    ThemeVariant.Concrete -> darkColorScheme(
        primary = Ash80, onPrimary = Ash30, primaryContainer = Ash30, onPrimaryContainer = Ash90,
        secondary = AshSecondary80, onSecondary = AshSecondary20,
        secondaryContainer = AshSecondary20, onSecondaryContainer = AshSecondary90,
        background = Concrete10, onBackground = Concrete90, surface = Concrete10, onSurface = Concrete90,
        surfaceVariant = Concrete20, onSurfaceVariant = Concrete80, error = Error80, outline = Concrete60,
    )
    ThemeVariant.Ink -> darkColorScheme(
        primary = Ink80, onPrimary = Ink30, primaryContainer = Ink30, onPrimaryContainer = Ink90,
        secondary = InkSecondary80, onSecondary = InkSecondary20,
        secondaryContainer = InkSecondary20, onSecondaryContainer = InkSecondary90,
        background = Concrete10, onBackground = Concrete90, surface = Concrete10, onSurface = Concrete90,
        surfaceVariant = Concrete20, onSurfaceVariant = Concrete90, error = Error80, outline = Concrete70,
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
