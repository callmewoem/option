package com.habitsfirst.androidclone.domain.model

/**
 * The user's chosen appearance for "app mode" screens (see
 * [com.habitsfirst.androidclone.ui.theme.LockeMode.App]) -- Light, Dark, or following
 * the system setting. This only ever swaps that one palette: "enforcement mode" screens
 * (the block cover, curfew, morning lock, penalty sheet) always stay dark/iron
 * regardless of this setting, since which mode those are in is a design decision about
 * what the screen is doing, not a user preference -- see
 * [com.habitsfirst.androidclone.ui.theme.LockeTheme].
 */
enum class ThemeMode(val displayName: String) {
    Light("Light"),
    Dark("Dark"),
    System("Follow system");

    companion object {
        val DEFAULT = System

        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
