package com.locke.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Locke's entire palette. Five materials -- iron, bone, verdigris, brass, oxide -- and
 * nothing else; see the design spec's "Visual tokens" section. Each color keeps exactly
 * one meaning everywhere it appears:
 *
 * - [Verdigris] is the only "safe/structural" color -- gating actions, progress fill,
 *   anything that moves a habit or a lock forward.
 * - [Brass]/[BrassLight] is *earned only* -- tokens, streaks, the lootbox. Never used to
 *   decorate something the user didn't win.
 * - [Oxide]/[OxideLight] is *cost only* -- penalties, scarred days, antihabit slips.
 *   Never reused for generic "error" chrome that isn't actually a cost.
 *
 * There's no separate light/dark *theme* -- see [LockeMode]. [Iron]/[Iron2] are
 * enforcement-mode surfaces, [Bone]/[BoneIn] are app-mode surfaces, and [Concrete] is
 * the dead page color behind the app (status/nav bar scrim, never a content surface).
 */
object LockeColor {
    /** Enforcement-mode page surface: the block cover, curfew, morning lock, penalty sheet. */
    val Iron = Color(0xFF1D2126)

    /** Enforcement-mode card surface, one step up from [Iron]. */
    val Iron2 = Color(0xFF282D34)

    /** App-mode page surface: Today, Stats, Settings, onboarding, everything "navigating". */
    val Bone = Color(0xFFEDECE5)

    /** App-mode card surface, one step in from [Bone]. */
    val BoneIn = Color(0xFFE2E1D8)

    /** The page itself, behind the app -- system bars only, never a content surface. */
    val Concrete = Color(0xFFD6D5CC)

    /** Safe / structural / gating. Progress fill, primary actions, "this moves you forward". */
    val Verdigris = Color(0xFF2C6A5D)
    val VerdigrisLight = Color(0xFF4E9484)

    /** Earned only -- tokens, streaks, the lootbox. Never decorative. */
    val Brass = Color(0xFFA9801F)
    val BrassLight = Color(0xFFD9B450)

    /** Cost only -- penalties, scarred days, antihabit slips. Never generic "error" chrome. */
    val Oxide = Color(0xFF9A3D2C)
    val OxideLight = Color(0xFFC97A63)

    /** Text/icons on [Iron]/[Iron2] -- full-strength bone. */
    val OnIron = Bone

    /** Muted text on [Iron]/[Iron2] -- captions, secondary lines. */
    val OnIronMuted = Bone.copy(alpha = 0.64f)

    /** Text/icons on [Bone]/[BoneIn] -- full-strength iron. */
    val OnBone = Iron

    /** Muted text on [Bone]/[BoneIn] -- captions, secondary lines. */
    val OnBoneMuted = Iron.copy(alpha = 0.60f)

    /** Reads on [Verdigris] or [Oxide] fills alike -- both are dark enough for bone text. */
    val OnVerdigris = Bone
    val OnOxide = Bone

    /** Brass is light enough that dark iron text reads better on it than bone would. */
    val OnBrass = Iron

    /** Hairline inset borders -- app mode (on [Bone]/[BoneIn]) and enforcement mode (on [Iron]/[Iron2]). */
    val BorderOnBone = Iron.copy(alpha = 0.14f)
    val BorderOnIron = Bone.copy(alpha = 0.16f)

    /** The permanent-list "no bypass" outline -- near-black, deliberately heavier than [BorderOnBone]. */
    val PermanentOutline = Iron.copy(alpha = 0.9f)

    /** "Just tracked" chip -- consequence-neutral grey, on either surface. */
    val NeutralOnBone = Iron.copy(alpha = 0.5f)
    val NeutralOnIron = Bone.copy(alpha = 0.5f)

    /** The scarred/broken heatmap cell -- always oxide, never reinterpreted (spec §4). */
    val Scarred = Oxide

    /** The gold-star cosmetic heatmap cell -- always brass. */
    val GoldStar = Brass
}
