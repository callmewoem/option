package com.habitsfirst.androidclone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What [LockeCat] is feeling -- picked by the caller to match what's actually going on. */
enum class CatMood {
    /** Head tilted, eyes wide -- "what's this?" Used for first impressions and empty states. */
    Curious,

    /** A calm little smile -- things are fine, nothing owed. Used when a day's fully done. */
    Content,

    /** Eyes shut, whiskers relaxed -- used only for the bedtime curfew, where it fits literally. */
    Sleepy,

    /** Eyes bright, mouth open -- used for the one genuinely celebratory moment, the lootbox. */
    Excited,
}

/**
 * A small flat-vector mascot -- circle head, triangle ears, a couple of whiskers --
 * drawn from primitive shapes rather than an imported image, so it's as cheap and
 * dependency-free as everything else in this theme. Used sparingly at a handful of
 * meaningful moments (see call sites), not as wallpaper: a little warmth and
 * personality without undercutting that Locke is still a tool for holding the line.
 */
@Composable
fun LockeCat(
    mood: CatMood,
    modifier: Modifier = Modifier,
    furColor: Color = Color(0xFFE8A94A),
    lineColor: Color = Color(0xFF2A1F14),
    size: Dp = 56.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val headRadius = w * 0.38f
        val center = Offset(w / 2f, h * 0.56f)
        val strokeW = w * 0.035f

        // Ears: two simple filled triangles behind the head circle.
        val earHeight = headRadius * 0.9f
        val earHalfWidth = headRadius * 0.55f
        listOf(-1f, 1f).forEach { side ->
            val earBaseX = center.x + side * headRadius * 0.62f
            val earBaseY = center.y - headRadius * 0.62f
            val path = Path().apply {
                moveTo(earBaseX - earHalfWidth * 0.5f, earBaseY)
                lineTo(earBaseX + side * earHalfWidth * 0.5f, earBaseY - earHeight)
                lineTo(earBaseX + earHalfWidth * 0.6f, earBaseY + earHeight * 0.15f)
                close()
            }
            drawPath(path, color = furColor)
            drawPath(path, color = lineColor, style = Stroke(width = strokeW))
        }

        // Head.
        drawCircle(color = furColor, radius = headRadius, center = center)
        drawCircle(color = lineColor, radius = headRadius, center = center, style = Stroke(width = strokeW))

        val eyeY = center.y - headRadius * 0.08f
        val eyeDx = headRadius * 0.42f
        val eyeRadius = headRadius * 0.14f

        when (mood) {
            CatMood.Sleepy -> {
                // Closed eyes: a gentle downward curve, drawn as arcs rather than lines.
                listOf(-1f, 1f).forEach { side ->
                    val eyeCenter = Offset(center.x + side * eyeDx, eyeY)
                    drawArc(
                        color = lineColor,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(eyeCenter.x - eyeRadius, eyeCenter.y - eyeRadius * 0.6f),
                        size = androidx.compose.ui.geometry.Size(eyeRadius * 2, eyeRadius * 1.2f),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    )
                }
            }
            CatMood.Excited -> {
                listOf(-1f, 1f).forEach { side ->
                    drawCircle(
                        color = lineColor,
                        radius = eyeRadius * 1.15f,
                        center = Offset(center.x + side * eyeDx, eyeY),
                    )
                }
            }
            CatMood.Curious, CatMood.Content -> {
                listOf(-1f, 1f).forEach { side ->
                    drawCircle(
                        color = lineColor,
                        radius = eyeRadius,
                        center = Offset(center.x + side * eyeDx, eyeY),
                    )
                }
            }
        }

        // Nose: a tiny filled triangle.
        val noseY = center.y + headRadius * 0.18f
        val noseSize = headRadius * 0.16f
        val nosePath = Path().apply {
            moveTo(center.x - noseSize, noseY)
            lineTo(center.x + noseSize, noseY)
            lineTo(center.x, noseY + noseSize)
            close()
        }
        drawPath(nosePath, color = lineColor)

        // Mouth.
        val mouthTop = noseY + noseSize
        when (mood) {
            CatMood.Excited -> {
                drawArc(
                    color = lineColor,
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(center.x - headRadius * 0.3f, mouthTop - headRadius * 0.1f),
                    size = androidx.compose.ui.geometry.Size(headRadius * 0.6f, headRadius * 0.4f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
            CatMood.Content -> {
                drawArc(
                    color = lineColor,
                    startAngle = 25f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(center.x - headRadius * 0.28f, mouthTop - headRadius * 0.18f),
                    size = androidx.compose.ui.geometry.Size(headRadius * 0.56f, headRadius * 0.3f),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
            CatMood.Curious, CatMood.Sleepy -> {
                drawLine(
                    color = lineColor,
                    start = Offset(center.x - headRadius * 0.14f, mouthTop),
                    end = Offset(center.x + headRadius * 0.14f, mouthTop),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Whiskers -- three short lines per side, out from the muzzle.
        val whiskerY = mouthTop - headRadius * 0.05f
        listOf(-1f, 1f).forEach { side ->
            for (i in 0..2) {
                val y = whiskerY + (i - 1) * headRadius * 0.16f
                drawLine(
                    color = lineColor.copy(alpha = 0.55f),
                    start = Offset(center.x + side * headRadius * 0.55f, y),
                    end = Offset(center.x + side * headRadius * 0.98f, y - side * headRadius * 0.06f),
                    strokeWidth = strokeW * 0.6f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
