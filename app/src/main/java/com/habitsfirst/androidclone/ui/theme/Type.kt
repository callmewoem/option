package com.habitsfirst.androidclone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * One family, worked on weight/spacing instead of piling on decoration -- see the design
 * spec's "Type" section. There's no Archivo (or other variable-width) font bundled here,
 * so the width axis it calls for is approximated: tighter negative letter-spacing plus a
 * heavier weight reads "condensed" for dense labels/nav, and looser positive
 * letter-spacing plus [FontWeight.Black] reads "expanded" for the numbers meant to be
 * felt. [FEATURE_TABULAR_NUMS] keeps every numeral the same width so a live-updating
 * countdown or streak count never shifts the layout around it.
 *
 * The general text scale below stays quiet and legible -- app mode is for reading and
 * deciding, not for shouting. The one loud move is [feltNumber]: a lone big number with
 * a small caption, used for anything that should land in the gut (habits left, minutes
 * left, a streak) rather than just be read. It sits outside [LockeTypography]'s fixed
 * roles since its size varies by context (a Today banner countdown is bigger than a
 * stat tile) -- callers pick a size and get the same weight/spacing treatment.
 */
private const val FEATURE_TABULAR_NUMS = "tnum"

fun feltNumber(size: TextUnit, letterSpacing: TextUnit = 0.sp): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Black,
    fontSize = size,
    lineHeight = size * 1.02f,
    letterSpacing = letterSpacing,
    fontFeatureSettings = FEATURE_TABULAR_NUMS,
)

/** The tight, all-caps kicker/nav-label treatment -- as close to "condensed" as a system font gets. */
fun condensedLabel(size: TextUnit, letterSpacing: TextUnit = 0.5.sp): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = size,
    lineHeight = size * 1.25f,
    letterSpacing = letterSpacing,
)

val LockeTypography = Typography(
    displayLarge = feltNumber(56.sp),
    displayMedium = feltNumber(44.sp),
    displaySmall = feltNumber(34.sp),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp,
    ),
    // labelLarge/Medium/Small are the app's dense-label voice: nav, kickers, chip text,
    // countdown captions -- always paired with a felt number or a card title, never body copy.
    labelLarge = condensedLabel(13.sp, letterSpacing = 0.4.sp),
    labelMedium = condensedLabel(12.sp, letterSpacing = 0.6.sp),
    labelSmall = condensedLabel(10.5.sp, letterSpacing = 0.8.sp),
)
