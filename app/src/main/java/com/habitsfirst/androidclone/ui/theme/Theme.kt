package com.habitsfirst.androidclone.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.habitsfirst.androidclone.domain.model.ThemeMode

/**
 * Locke's one bold move (design spec §3): every screen is either *navigating* (app
 * mode -- bone, light) or *taking something away / demanding something* (enforcement
 * mode -- iron, dark). This isn't a light/dark theme preference -- it never follows the
 * system setting -- it's picked per screen by what that screen is doing. See
 * [LockeTheme].
 */
enum class LockeMode { App, Enforcement }

val LocalLockeMode = staticCompositionLocalOf { LockeMode.App }

/**
 * Whether the *currently active* surface is dark -- true for [LockeMode.Enforcement]
 * always, and for [LockeMode.App] whenever the user's [ThemeMode] preference resolves
 * to dark. Components that hand-pick a color instead of reading it off
 * [MaterialTheme.colorScheme] (see e.g. `LockeControls.kt`'s `countdownColor`) should
 * branch on this rather than on [LocalLockeMode] directly, since "which surface family
 * am I drawing on" and "which design mode is this screen in" are no longer the same
 * question once app mode itself can be dark.
 */
val LocalLockeSurfaceIsDark = staticCompositionLocalOf { false }

/**
 * Builds a Material3 [androidx.compose.material3.ColorScheme] entirely from
 * [LockeColor] -- iron/bone/verdigris/brass/oxide, no other hue anywhere. Intermediate
 * "container" tones are linear blends toward the mode's own surface rather than a
 * separate design token, since the spec's palette has no room for a sixth color.
 * [ColorScheme.surfaceTint][androidx.compose.material3.ColorScheme] is pinned to
 * [Color.Transparent] so Material3's automatic dark-theme elevation overlay can't tint
 * an iron card green on its own -- every surface stays flat and every accent stays
 * intentional.
 */
private fun appColorScheme() = lightColorScheme(
    background = LockeColor.Bone,
    onBackground = LockeColor.OnBone,
    surface = LockeColor.Bone,
    onSurface = LockeColor.OnBone,
    surfaceVariant = LockeColor.BoneIn,
    onSurfaceVariant = LockeColor.OnBoneMuted,
    surfaceContainerLowest = LockeColor.Bone,
    surfaceContainerLow = LockeColor.Bone,
    surfaceContainer = LockeColor.BoneIn,
    surfaceContainerHigh = lerp(LockeColor.BoneIn, LockeColor.Iron, 0.06f),
    surfaceContainerHighest = lerp(LockeColor.BoneIn, LockeColor.Iron, 0.12f),
    surfaceDim = LockeColor.BoneIn,
    surfaceBright = LockeColor.Bone,
    surfaceTint = Color.Transparent,
    inverseSurface = LockeColor.Iron,
    inverseOnSurface = LockeColor.Bone,
    primary = LockeColor.Verdigris,
    onPrimary = LockeColor.OnVerdigris,
    primaryContainer = lerp(LockeColor.Bone, LockeColor.Verdigris, 0.16f),
    onPrimaryContainer = LockeColor.Verdigris,
    // Material3's own default styling for FilterChip/SegmentedButton (a plain "this
    // option is selected" state, not a reward) pulls straight from
    // secondary/secondaryContainer -- so these two stay a neutral iron/bone blend
    // rather than brass, or every filter chip and segmented button in the app would
    // read as "earned" (design spec §2, §4: brass is earned-only, never decorative).
    // Anywhere something is genuinely earned, it's colored from LockeColor.Brass
    // directly instead of through this role.
    secondary = lerp(LockeColor.Bone, LockeColor.Iron, 0.55f),
    onSecondary = LockeColor.Bone,
    secondaryContainer = lerp(LockeColor.Bone, LockeColor.Iron, 0.10f),
    onSecondaryContainer = lerp(LockeColor.Bone, LockeColor.Iron, 0.6f),
    tertiary = lerp(LockeColor.Bone, LockeColor.Iron, 0.55f),
    onTertiary = LockeColor.Bone,
    tertiaryContainer = lerp(LockeColor.Bone, LockeColor.Iron, 0.10f),
    onTertiaryContainer = lerp(LockeColor.Bone, LockeColor.Iron, 0.6f),
    error = LockeColor.Oxide,
    onError = LockeColor.OnOxide,
    errorContainer = lerp(LockeColor.Bone, LockeColor.Oxide, 0.16f),
    onErrorContainer = LockeColor.Oxide,
    outline = LockeColor.BorderOnBone,
    outlineVariant = LockeColor.BorderOnBone.copy(alpha = 0.08f),
)

/**
 * [appColorScheme]'s dark counterpart -- same roles, same accent meanings, swapped onto
 * [LockeColor.Slate]/[LockeColor.SlateIn] instead of [LockeColor.Bone]/[LockeColor.BoneIn].
 * Used for [LockeMode.App] screens only, when the user's [ThemeMode] preference resolves
 * to dark; [enforcementColorScheme] is separate and unaffected by that preference.
 */
private fun appDarkColorScheme() = darkColorScheme(
    background = LockeColor.Slate,
    onBackground = LockeColor.OnSlate,
    surface = LockeColor.Slate,
    onSurface = LockeColor.OnSlate,
    surfaceVariant = LockeColor.SlateIn,
    onSurfaceVariant = LockeColor.OnSlateMuted,
    surfaceContainerLowest = LockeColor.Slate,
    surfaceContainerLow = LockeColor.Slate,
    surfaceContainer = LockeColor.SlateIn,
    surfaceContainerHigh = lerp(LockeColor.SlateIn, LockeColor.Bone, 0.06f),
    surfaceContainerHighest = lerp(LockeColor.SlateIn, LockeColor.Bone, 0.12f),
    surfaceDim = LockeColor.Slate,
    surfaceBright = LockeColor.SlateIn,
    surfaceTint = Color.Transparent,
    inverseSurface = LockeColor.Bone,
    inverseOnSurface = LockeColor.Slate,
    primary = LockeColor.VerdigrisLight,
    onPrimary = LockeColor.Slate,
    primaryContainer = lerp(LockeColor.SlateIn, LockeColor.Verdigris, 0.55f),
    onPrimaryContainer = LockeColor.VerdigrisLight,
    // See appColorScheme()'s comment -- kept neutral for the same reason: a default
    // FilterChip/SegmentedButton selection is not a reward.
    secondary = lerp(LockeColor.Slate, LockeColor.Bone, 0.55f),
    onSecondary = LockeColor.Slate,
    secondaryContainer = lerp(LockeColor.Slate, LockeColor.Bone, 0.14f),
    onSecondaryContainer = lerp(LockeColor.Slate, LockeColor.Bone, 0.65f),
    tertiary = lerp(LockeColor.Slate, LockeColor.Bone, 0.55f),
    onTertiary = LockeColor.Slate,
    tertiaryContainer = lerp(LockeColor.Slate, LockeColor.Bone, 0.14f),
    onTertiaryContainer = lerp(LockeColor.Slate, LockeColor.Bone, 0.65f),
    error = LockeColor.OxideLight,
    onError = LockeColor.Slate,
    errorContainer = lerp(LockeColor.SlateIn, LockeColor.Oxide, 0.5f),
    onErrorContainer = LockeColor.OxideLight,
    outline = LockeColor.BorderOnSlate,
    outlineVariant = LockeColor.BorderOnSlate.copy(alpha = 0.08f),
)

private fun enforcementColorScheme() = darkColorScheme(
    background = LockeColor.Iron,
    onBackground = LockeColor.OnIron,
    surface = LockeColor.Iron,
    onSurface = LockeColor.OnIron,
    surfaceVariant = LockeColor.Iron2,
    onSurfaceVariant = LockeColor.OnIronMuted,
    surfaceContainerLowest = LockeColor.Iron,
    surfaceContainerLow = LockeColor.Iron,
    surfaceContainer = LockeColor.Iron2,
    surfaceContainerHigh = lerp(LockeColor.Iron2, LockeColor.Bone, 0.06f),
    surfaceContainerHighest = lerp(LockeColor.Iron2, LockeColor.Bone, 0.12f),
    surfaceDim = LockeColor.Iron,
    surfaceBright = LockeColor.Iron2,
    surfaceTint = Color.Transparent,
    inverseSurface = LockeColor.Bone,
    inverseOnSurface = LockeColor.Iron,
    primary = LockeColor.VerdigrisLight,
    onPrimary = LockeColor.Iron,
    primaryContainer = lerp(LockeColor.Iron2, LockeColor.Verdigris, 0.55f),
    onPrimaryContainer = LockeColor.VerdigrisLight,
    // See appColorScheme()'s comment -- kept neutral for the same reason: a default
    // FilterChip/SegmentedButton selection is not a reward.
    secondary = lerp(LockeColor.Iron, LockeColor.Bone, 0.55f),
    onSecondary = LockeColor.Iron,
    secondaryContainer = lerp(LockeColor.Iron, LockeColor.Bone, 0.14f),
    onSecondaryContainer = lerp(LockeColor.Iron, LockeColor.Bone, 0.65f),
    tertiary = lerp(LockeColor.Iron, LockeColor.Bone, 0.55f),
    onTertiary = LockeColor.Iron,
    tertiaryContainer = lerp(LockeColor.Iron, LockeColor.Bone, 0.14f),
    onTertiaryContainer = lerp(LockeColor.Iron, LockeColor.Bone, 0.65f),
    error = LockeColor.OxideLight,
    onError = LockeColor.Iron,
    errorContainer = lerp(LockeColor.Iron2, LockeColor.Oxide, 0.5f),
    onErrorContainer = LockeColor.OxideLight,
    outline = LockeColor.BorderOnIron,
    outlineVariant = LockeColor.BorderOnIron.copy(alpha = 0.08f),
)

/**
 * Root theme for every screen. [mode] is the whole design language in one switch --
 * pass [LockeMode.Enforcement] for anything taking something away or demanding
 * something (the block cover, curfew, the morning lock, the penalty sheet, the
 * lootbox reveal) and leave it at the [LockeMode.App] default everywhere else. Which
 * *mode* a screen is in is a design decision about what that screen *does*, not a user
 * preference, and [themeMode] can't change that -- but within [LockeMode.App] it picks
 * between [appColorScheme] and its dark counterpart, [appDarkColorScheme]. Enforcement
 * screens ignore [themeMode] entirely and always render dark.
 */
@Composable
fun LockeTheme(mode: LockeMode = LockeMode.App, themeMode: ThemeMode = ThemeMode.DEFAULT, content: @Composable () -> Unit) {
    val systemInDarkTheme = isSystemInDarkTheme()
    val appModeIsDark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemInDarkTheme
    }
    val surfaceIsDark = mode == LockeMode.Enforcement || appModeIsDark
    val colorScheme = when {
        mode == LockeMode.Enforcement -> enforcementColorScheme()
        appModeIsDark -> appDarkColorScheme()
        else -> appColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !surfaceIsDark
            insetsController.isAppearanceLightNavigationBars = !surfaceIsDark
        }
    }

    CompositionLocalProvider(
        LocalLockeMode provides mode,
        LocalLockeSurfaceIsDark provides surfaceIsDark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LockeTypography,
            shapes = LockeShapes,
            content = content,
        )
    }
}
