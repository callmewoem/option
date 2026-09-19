package com.locke.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Locke's palette. App mode (light) follows the "ecobrutalist" Today-screen design spec --
 * flat moss and bone, no gradients or shadows, terracotta for cost/avoid, no other hue.
 * Enforcement mode and app-mode dark ([Iron]/[Iron2]/[Slate]/[SlateIn]) have no spec of
 * their own yet, so they're left as they were.
 *
 * The legacy semantic roles ([Verdigris], [Brass], [Oxide], etc.) are kept and remapped
 * onto the tokens below rather than duplicated, so every existing App-mode screen picks
 * up the new palette through [Theme.kt] without a per-screen edit:
 * - [Verdigris] (structural/primary) -> [Moss].
 * - [Brass] (earned -- tokens, streaks, the lootbox) -> also [Moss]; the design spec's
 *   streak stat tile is filled moss, not a separate gold.
 * - [Oxide] (cost -- penalties, scarred days, antihabit slips) -> [Avoid] (terracotta).
 */
object LockeColor {
    /** Enforcement-mode page surface: the block cover, curfew, morning lock, penalty sheet. Unchanged -- out of scope for this redesign. */
    val Iron = Color(0xFF1D2126)

    /** Enforcement-mode card surface, one step up from [Iron]. Unchanged. */
    val Iron2 = Color(0xFF282D34)

    /** App-mode page surface when the user's appearance preference resolves to dark. Unchanged -- no dark counterpart in the design spec. */
    val Slate = Color(0xFF17191C)

    /** App-mode card surface when dark, one step up from [Slate]. Unchanged. */
    val SlateIn = Color(0xFF212327)

    /** The page itself, behind the app -- system bars only, never a content surface. Unchanged. */
    val Concrete = Color(0xFFD6D5CC)

    // -- App-mode (light) palette: the Today-screen design spec's tokens --------------

    /** App-mode page background. */
    val Bone = Color(0xFFECEADF)

    /** Pill/card surface. */
    val BoneIn = Color(0xFFF5F3EC)

    /** Completed-habit pill surface. */
    val SurfaceCompleted = Color(0xFFEFEDE4)

    /** Completed-habit pill border. */
    val SurfaceCompletedBorder = Color(0xFFE0DBCC)

    /** Hairline pill/card border. */
    val HabitBorder = Color(0xFFD6D1C4)

    /** Primary text. */
    val Ink = Color(0xFF2B2A25)

    /** Structural/primary moss -- gating actions, progress fill, the logo, primary round-button borders. */
    val Moss = Color(0xFF3B4A2C)

    /** The hero numeral's own shade -- a hair darker than [Moss]. */
    val MossDark = Color(0xFF34422A)

    /** Mid moss -- heatmap level 2, habit-type icon accents. */
    val MossMid = Color(0xFF8A9A68)

    /** Light moss -- heatmap level 1. */
    val MossLight = Color(0xFFC3CEA7)

    /** "HABITS LEFT" and other kicker labels. */
    val LabelOlive = Color(0xFF77805F)

    /** Muted body text. */
    val MutedText = Color(0xFF6B6558)

    /** Muted mono data text (progress counts, e.g. "0 / 1000 steps"). */
    val MutedTextData = Color(0xFF837C6D)

    /** Faintest muted text/icon tint. */
    val MutedTextFaint = Color(0xFF948D7E)

    /** Avoid/negative terracotta -- antihabit accents, the no-entry icon. */
    val Avoid = Color(0xFFB3766B)

    /** Swipe-reveal Delete action fill. */
    val DeleteFill = Color(0xFFECD5CF)

    /** Swipe-reveal Delete action text. */
    val DeleteText = Color(0xFF8A4A3F)

    /** Swipe-reveal Edit action fill. */
    val EditFill = Color(0xFFDFE4D2)

    /** Progress bar's empty track. */
    val EmptyTrack = Color(0xFFDCD8C9)

    /** Heatmap level-0 (empty) cell outline. */
    val HeatmapEmptyOutline = Color(0xFFD2CDBD)

    /** Strikethrough color on a completed habit's title. */
    val CompletedStrike = Color(0xFFA8A294)

    /** Habit-row type icon's default (incomplete, non-avoid) tint. */
    val HabitIconDefault = Color(0xFF5D6B4C)

    // -- Legacy semantic roles, remapped onto the palette above -----------------------

    /** Safe / structural / gating. Progress fill, primary actions, "this moves you forward". */
    val Verdigris = Moss
    val VerdigrisLight = MossMid

    /** Earned only -- tokens, streaks, the lootbox. Never decorative. */
    val Brass = Moss
    val BrassLight = MossMid

    /** Cost only -- penalties, scarred days, antihabit slips. Never generic "error" chrome. */
    val Oxide = Avoid
    val OxideLight = Color(0xFFC9968C)

    /** Text/icons on [Iron]/[Iron2] -- full-strength bone. */
    val OnIron = Bone

    /** Muted text on [Iron]/[Iron2] -- captions, secondary lines. */
    val OnIronMuted = Bone.copy(alpha = 0.64f)

    /** Text/icons on [Bone]/[BoneIn]. */
    val OnBone = Ink

    /** Muted text on [Bone]/[BoneIn] -- captions, secondary lines. */
    val OnBoneMuted = MutedText

    /** Text/icons on [Slate]/[SlateIn] -- full-strength bone, same as [OnIron]. */
    val OnSlate = Bone

    /** Muted text on [Slate]/[SlateIn] -- captions, secondary lines. */
    val OnSlateMuted = Bone.copy(alpha = 0.64f)

    /** Reads on [Verdigris] or [Oxide] fills alike -- both are dark enough for bone text. */
    val OnVerdigris = Bone
    val OnOxide = Bone

    /** [Brass] is now [Moss] (dark) -- bone text/icons read on it, same as [OnVerdigris]. */
    val OnBrass = Bone

    /** Hairline inset borders -- app mode light (on [Bone]/[BoneIn]), app mode dark (on [Slate]/[SlateIn]), and enforcement mode (on [Iron]/[Iron2]). */
    val BorderOnBone = HabitBorder
    val BorderOnSlate = Bone.copy(alpha = 0.16f)
    val BorderOnIron = Bone.copy(alpha = 0.16f)

    /** The permanent-list "no bypass" outline -- near-black, deliberately heavier than [BorderOnBone]. */
    val PermanentOutline = Ink.copy(alpha = 0.9f)

    /** "Just tracked" chip -- consequence-neutral grey, on any surface. */
    val NeutralOnBone = MutedTextFaint
    val NeutralOnSlate = Bone.copy(alpha = 0.5f)
    val NeutralOnIron = Bone.copy(alpha = 0.5f)

    /** The scarred/broken heatmap cell -- always oxide/terracotta, never reinterpreted (spec §4). */
    val Scarred = Oxide

    /** The gold-star cosmetic heatmap cell -- always brass/moss. */
    val GoldStar = Brass
}
