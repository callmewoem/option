package com.habitsfirst.androidclone.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared "printed slip / ledger" motifs -- Locke's visual signature. The app already
 * talks about itself in receipt/citation terms (a "Receipt" theme variant, monospace
 * "stamped label" numerics) -- these three primitives make that literal instead of
 * just implied, and get reused everywhere a moment needs to feel *issued* rather than
 * merely displayed: a rubber-stamped verdict, a torn perforation, a redeemed ticket's
 * notched edge, an itemized line with a dotted leader running out to its value.
 */

/**
 * A rotated, bordered stamp of all-caps monospace text -- "ON HOLD", "ALL CLEAR",
 * "LOGGED" -- reading like it was pressed onto the layout rather than laid out with it.
 */
@Composable
fun StampBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.error,
    rotationDegrees: Float = -8f,
) {
    Box(
        modifier = modifier
            .rotate(rotationDegrees)
            .border(BorderStroke(3.dp, color), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = color,
        )
    }
}

/** A dashed "tear here" rule -- a lighter-weight divider than a solid [androidx.compose.material3.HorizontalDivider]. */
@Composable
fun PerforatedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 6.dp,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f),
        )
    }
}

/**
 * The pair of half-circle "bites" out of a card's left/right edges that read as a
 * torn-off ticket stub. Overlay this on top of a full-bleed [Modifier] (e.g. via
 * [androidx.compose.foundation.layout.Box] + `matchParentSize()`) with [behindColor]
 * set to whatever actually shows through the notch -- the page background behind a
 * plain card, or an enclosing field's own color if the card sits inside one.
 */
@Composable
fun TicketNotches(behindColor: Color, modifier: Modifier = Modifier, notchSize: Dp = 22.dp) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -(notchSize / 2))
                .size(notchSize)
                .clip(CircleShape)
                .background(behindColor),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = notchSize / 2)
                .size(notchSize)
                .clip(CircleShape)
                .background(behindColor),
        )
    }
}

/**
 * One line of an itemized "docket" -- a label, a dotted leader running out to its
 * value, receipt-style ("Push-ups .......... 12 / 30"). Used anywhere the app is
 * listing things owed or things counted: [com.habitsfirst.androidclone.ui.block.BlockScreen]'s
 * remaining habits, Settings' reward-token tallies.
 */
@Composable
fun LedgerLineRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    dotColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .padding(horizontal = 8.dp),
        ) {
            drawLine(
                color = dotColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7f), 0f),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            maxLines = 1,
        )
    }
}
