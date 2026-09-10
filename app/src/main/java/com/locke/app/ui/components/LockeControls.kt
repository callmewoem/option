package com.locke.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locke.app.ui.theme.LockeColor
import com.locke.app.ui.theme.LockeMode
import com.locke.app.ui.theme.LocalLockeMode
import com.locke.app.ui.theme.feltNumber

/**
 * The one card shape used everywhere: inset (a hairline border, never a drop shadow),
 * one info unit per card. [borderColor] defaults to a plain hairline, but a highlighted
 * state (done, slipped, due) should pass its own accent -- never mix two unrelated
 * facts in one card (design spec §4).
 */
@Composable
fun LockeCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    shape: Shape = MaterialTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        androidx.compose.foundation.layout.Column(content = content)
    }
}

/**
 * What a chip's color signals -- coded by *consequence*, not by whatever internal data
 * model produced it (design spec §4). Never reinterpreted: [Abstinence] is always
 * oxide, [Permanent] is always a near-black outline, [Earned] is always brass.
 */
enum class ChipConsequence { Gates, Tracked, Abstinence, Permanent, Earned }

@Composable
private fun ChipConsequence.contentColor(): Color {
    val onIron = LocalLockeMode.current == LockeMode.Enforcement
    return when (this) {
        ChipConsequence.Gates -> MaterialTheme.colorScheme.primary
        ChipConsequence.Tracked -> if (onIron) LockeColor.NeutralOnIron else LockeColor.NeutralOnBone
        ChipConsequence.Abstinence -> LockeColor.Oxide
        ChipConsequence.Permanent -> if (onIron) LockeColor.OnIron else LockeColor.OnBone
        ChipConsequence.Earned -> LockeColor.Brass
    }
}

/** A small, consequence-coded pill -- "gates your apps," "just tracked," "abstinence," "permanent, no bypass," "earned." */
@Composable
fun ConsequenceChip(text: String, consequence: ChipConsequence, modifier: Modifier = Modifier) {
    val color = consequence.contentColor()
    val pillShape = RoundedCornerShape(50)
    // Permanent is drawn as an outline rather than a fill -- "no bypass" reads as a
    // harder edge than every other chip, which are soft tinted fills.
    val base = if (consequence == ChipConsequence.Permanent) {
        Modifier.border(BorderStroke(1.dp, color), pillShape)
    } else {
        Modifier.background(color.copy(alpha = 0.12f), pillShape)
    }
    Row(modifier = modifier.then(base).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** One filled primary button per screen, max (design spec §4). Verdigris fill by default. */
@Composable
fun LockePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        leadingIcon?.let {
            it()
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(end = 4.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Ghost/outline secondary action. */
@Composable
fun LockeGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The honest-but-discouraged option -- "give up for today," "take the penalty," "mark
 * it done anyway." Always present when it applies, never hidden behind a gesture
 * (design spec §4, §6.2).
 */
@Composable
fun LockeQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/**
 * The app's signature move: a lone big number with a small caption, for anything that
 * should land in the gut rather than just be read -- habits left, minutes left, a
 * streak (design spec §2). [color] should escalate with urgency for a countdown; see
 * [countdownColor].
 */
@Composable
fun BigNumber(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    captionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: TextUnit = 56.sp,
    align: TextAlign = TextAlign.Start,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier, horizontalAlignment = if (align == TextAlign.Center) androidx.compose.ui.Alignment.CenterHorizontally else androidx.compose.ui.Alignment.Start) {
        Text(text = value, style = feltNumber(size), color = color, textAlign = align)
        Text(text = caption, style = MaterialTheme.typography.labelLarge, color = captionColor, textAlign = align)
    }
}

/**
 * Countdown urgency ramp shared by every hard deadline (design spec §4): calm/neutral
 * verdigris while there's plenty of time, brass as it tightens, oxide once the deadline
 * has actually passed. [fractionElapsed] is 0f at the start of the window and 1f at the
 * deadline; [isPastDeadline] overrides straight to oxide regardless.
 */
@Composable
fun countdownColor(fractionElapsed: Float, isPastDeadline: Boolean): Color {
    val onIron = LocalLockeMode.current == LockeMode.Enforcement
    return when {
        isPastDeadline -> if (onIron) LockeColor.OxideLight else LockeColor.Oxide
        fractionElapsed >= 0.8f -> if (onIron) LockeColor.BrassLight else LockeColor.Brass
        else -> if (onIron) LockeColor.VerdigrisLight else LockeColor.Verdigris
    }
}

/** "MM:SS" for anything under an hour left, "H:MM" past that. */
fun formatCountdown(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val hours = clamped / 3600
    val minutes = (clamped % 3600) / 60
    val seconds = clamped % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
