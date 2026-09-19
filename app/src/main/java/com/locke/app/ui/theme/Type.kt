package com.locke.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.locke.app.R

/**
 * The Today-screen design spec's three-family type system (see DESIGN.md): Fraunces
 * italic 600 for the one big hero numeral only, Space Grotesk for all other UI text, and
 * Space Mono for data/labels ("HABITS LEFT", step/minute counts, the progress fraction).
 * Never Inter/Roboto/Arial -- [LockeTypography] below runs entirely on [SpaceGrotesk],
 * and [FrauncesItalic]/[SpaceMono] are used explicitly by the components that call for
 * them ([frauncesNumeral], [spaceMono]) rather than through a [Typography] role, since
 * neither is meant to appear anywhere else in the type scale.
 */
val FrauncesItalic = FontFamily(Font(R.font.fraunces_italic_semibold, FontWeight.SemiBold, FontStyle.Italic))

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/**
 * One family, worked on weight/spacing instead of piling on decoration -- see the design
 * spec's "Type" section. There's no Archivo (or other variable-width) font bundled here,
 * so the width axis it calls for is approximated: tighter negative letter-spacing plus a
 * heavier weight reads "condensed" for dense labels/nav, and looser positive
 * letter-spacing plus [FontWeight.Black] reads "expanded" for the numbers meant to be
 * felt. [FEATURE_TABULAR_NUMS] keeps every numeral the same width so a live-updating
 * countdown or streak count never shifts the layout around it.
 *
 * [feltNumber] is unrelated to the Today-screen redesign's [frauncesNumeral] -- it's
 * still used by enforcement-mode screens (the check-in countdown, penalty sheets), which
 * have no design spec of their own yet and keep the system-default font.
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
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.SemiBold,
    fontSize = size,
    lineHeight = size * 1.25f,
    letterSpacing = letterSpacing,
)

/**
 * Today's one hero moment (design spec §2): Fraunces italic 600, one size at all counts
 * (116sp/177sp line-height/-6sp letter-spacing per the reference), tabular so the digit
 * count changing never reflows the heatmap beside it.
 */
fun frauncesNumeral(size: TextUnit = 116.sp, lineHeight: TextUnit = 177.sp, letterSpacing: TextUnit = (-6).sp): TextStyle = TextStyle(
    fontFamily = FrauncesItalic,
    fontWeight = FontWeight.SemiBold,
    fontStyle = FontStyle.Italic,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = FEATURE_TABULAR_NUMS,
    // Android pads a text layout above/below the glyphs' own metrics by default -- at
    // this size that padding is large enough to throw off the numeral's alignment
    // against the heatmap beside it, so it's turned off here.
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Space Mono for data and labels: step/minute stats, the progress fraction, "that's all for today". */
fun spaceMono(size: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = 0.sp): TextStyle = TextStyle(
    fontFamily = SpaceMono,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size * 1.2f,
    letterSpacing = letterSpacing,
    fontFeatureSettings = FEATURE_TABULAR_NUMS,
)

val LockeTypography = Typography(
    displayLarge = feltNumber(56.sp),
    displayMedium = feltNumber(44.sp),
    displaySmall = feltNumber(34.sp),
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SpaceGrotesk,
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
