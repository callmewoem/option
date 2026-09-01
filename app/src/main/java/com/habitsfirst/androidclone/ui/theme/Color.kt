package com.habitsfirst.androidclone.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Eco-brutalist palette: raw, flat, low-saturation tones -- poured concrete and paper
 * for structure, a single loud "organic" accent for signal. No gradients, no pastels.
 * Every [ThemeVariant] shares the same concrete/paper neutrals below and only swaps
 * its accent pair, so unlocking a new theme (see the lootbox system) never changes the
 * app's underlying brutalist shape/type language -- just its signal color.
 */

// Concrete / paper neutrals -- shared by every variant.
val Concrete10 = Color(0xFF141311)
val Concrete20 = Color(0xFF211F1B)
val Concrete30 = Color(0xFF332F29)
val Concrete40 = Color(0xFF4A443B)
val Concrete50 = Color(0xFF615A4E)
val Concrete60 = Color(0xFF7C7364)
val Concrete70 = Color(0xFF9A8F7D)
val Concrete80 = Color(0xFFBBAE99)
val Concrete90 = Color(0xFFDDD2C0)
val Concrete95 = Color(0xFFEDE6D8)
val Concrete99 = Color(0xFFF7F3EA) // "paper" background

val Error40 = Color(0xFF8C1D14)
val Error80 = Color(0xFFFFB4A4)

/** Moss -- default. The "eco" half of eco-brutalist: an overgrown, unapologetic green. */
val Moss30 = Color(0xFF243D00)
val Moss40 = Color(0xFF3C5700)
val Moss80 = Color(0xFFA3D256)
val Moss90 = Color(0xFFCDEB9A)
val MossSecondary40 = Color(0xFFA6431A) // rust, paired as secondary
val MossSecondary80 = Color(0xFFFFB59B)
val MossSecondary20 = Color(0xFF5C1F00)
val MossSecondary90 = Color(0xFFFFDBCB)

/** Rust -- clay/terracotta as the primary signal, moss demoted to secondary. */
val Rust30 = Color(0xFF5C1F00)
val Rust40 = Color(0xFF8C3316)
val Rust80 = Color(0xFFFFB59B)
val Rust90 = Color(0xFFFFDBCB)
val RustSecondary40 = Color(0xFF4F7300)
val RustSecondary80 = Color(0xFFA3D256)
val RustSecondary20 = Color(0xFF243D00)
val RustSecondary90 = Color(0xFFCDEB9A)

/** Concrete -- monochrome poured-slab variant, only a dull mustard breaks the gray. */
val Ash30 = Color(0xFF2E2B25)
val Ash40 = Color(0xFF4A443B)
val Ash80 = Color(0xFFC9BCA3)
val Ash90 = Color(0xFFE4D9C4)
val AshSecondary40 = Color(0xFF7A5F00)
val AshSecondary80 = Color(0xFFE8C355)
val AshSecondary20 = Color(0xFF453600)
val AshSecondary90 = Color(0xFFF5DE93)

/** Ink -- the loudest variant: stark near-black on paper, blood-rust for signal. */
val Ink30 = Color(0xFF1C1B19)
val Ink40 = Color(0xFF2E2C28)
val Ink80 = Color(0xFFD8D2C6)
val Ink90 = Color(0xFFECE7DB)
val InkSecondary40 = Color(0xFFA6191A)
val InkSecondary80 = Color(0xFFFFB3AE)
val InkSecondary20 = Color(0xFF680002)
val InkSecondary90 = Color(0xFFFFDAD5)

/** Modern -- unlocked by default alongside Moss. A cool slate-blue signal, mustard demoted to secondary: the same brutalist shapes read calmer and more contemporary than the earthy variants. */
val Slate30 = Color(0xFF1A3547)
val Slate40 = Color(0xFF2C5069)
val Slate80 = Color(0xFFA9CCDF)
val Slate90 = Color(0xFFD1E6F0)
val SlateSecondary40 = Color(0xFF8A5A00)
val SlateSecondary80 = Color(0xFFFFC066)
val SlateSecondary20 = Color(0xFF4D3000)
val SlateSecondary90 = Color(0xFFFFE0A6)

/** Receipt -- carbon-copy black on paper, a "past due" stamp red for signal. Neutral and cooler than Ink's warm near-black, and the red leans clean/scarlet rather than blood-rust. */
val Carbon30 = Color(0xFF1A1A1A)
val Carbon40 = Color(0xFF2B2B2B)
val Carbon80 = Color(0xFFD6D6D6)
val Carbon90 = Color(0xFFECECEC)
val CarbonSecondary40 = Color(0xFFB3261E)
val CarbonSecondary80 = Color(0xFFFFB4A9)
val CarbonSecondary20 = Color(0xFF690500)
val CarbonSecondary90 = Color(0xFFFFDAD4)
