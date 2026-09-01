package com.habitsfirst.androidclone.domain.model

/**
 * Codes that unlock theme variants for free, bypassing the daily lootbox grind -- e.g.
 * one shared in a changelog, on social media, or with friends. Redeemed via
 * [com.habitsfirst.androidclone.data.repository.LootboxRepository.redeemThemeCode].
 * Matching is case-insensitive and ignores surrounding whitespace.
 */
object ThemeRedeemCode {
    private val CODES: Map<String, Set<ThemeVariant>> = mapOf(
        "RUSTBELT" to setOf(ThemeVariant.Rust),
        "CONCRETE" to setOf(ThemeVariant.Concrete),
        "INKWELL" to setOf(ThemeVariant.Ink),
        "GOTRECEIPTS" to setOf(ThemeVariant.Receipt),
        "LOCKEALL" to ThemeVariant.entries.toSet(),
    )

    /** The variants [code] grants, or null if it doesn't match any known code. */
    fun resolve(code: String): Set<ThemeVariant>? = CODES[code.trim().uppercase()]
}

/** Outcome of redeeming a theme code, so the UI can show a specific message for each. */
sealed interface ThemeCodeResult {
    /** [variants] is only what was newly unlocked -- already-unlocked variants the same code also grants are excluded. */
    data class Unlocked(val variants: Set<ThemeVariant>) : ThemeCodeResult
    data object AlreadyUnlocked : ThemeCodeResult
    data object Invalid : ThemeCodeResult
}
