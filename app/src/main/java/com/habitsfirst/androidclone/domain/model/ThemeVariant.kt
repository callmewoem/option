package com.habitsfirst.androidclone.domain.model

/**
 * A lootbox-unlockable accent pair for the eco-brutalist theme (see
 * [com.habitsfirst.androidclone.ui.theme.LockeTheme]). Shapes and type never change
 * between variants -- only the primary/secondary signal color does. [Moss] and [Modern]
 * are unlocked by default; the rest (including [Receipt]) are won from the lootbox.
 */
enum class ThemeVariant(val displayName: String) {
    Moss("Moss"),
    Modern("Modern"),
    Rust("Rust"),
    Concrete("Concrete"),
    Ink("Ink"),
    Receipt("Receipt");

    companion object {
        val DEFAULT = Moss

        fun fromId(id: String?): ThemeVariant = entries.firstOrNull { it.name == id } ?: DEFAULT
    }
}
