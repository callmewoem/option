package com.habitsfirst.androidclone.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a composable to a PNG file for sharing -- the "shareable stats card" export
 * (see [com.habitsfirst.androidclone.ui.habits.ShareableStatsCard]), but not tied to
 * that composable specifically. Pairs with [rememberCaptureGraphicsLayer]: host that
 * layer on the composable to capture (invisibly, if it shouldn't be shown on-screen),
 * then call [captureToPng] once it's actually been drawn at least one frame.
 */
object ComposeCaptureUtil {
    private const val SHARE_CARD_DIR = "share_cards"
    private const val PNG_QUALITY = 100

    /**
     * Reads back everything [GraphicsLayer.record] captured, as an Android [Bitmap],
     * and writes it to this app's cache dir as `stats_card.png` -- a fixed name, since
     * only one shared card is ever in flight at a time, so each capture simply
     * overwrites the last rather than piling up a new timestamped file in cache on
     * every Share tap. Must be called after the layer's host composable has actually
     * drawn a frame -- e.g. from a coroutine launched by a click handler, once the
     * capture host is known to be in composition. [GraphicsLayer.toImageBitmap] itself
     * runs on the calling dispatcher (it needs the original rendering context); only
     * the bitmap compress/file write below is moved to [Dispatchers.IO].
     */
    suspend fun captureToPng(context: Context, layer: GraphicsLayer): File? {
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_CARD_DIR).apply { mkdirs() }
            val file = File(dir, "stats_card.png")
            try {
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out) }
                file
            } catch (e: IOException) {
                null
            }
        }
    }

    /** A `content://` [Uri] for a file this util already saved, via the app's shared [ImageStore.uriForFile]. */
    fun uriFor(context: Context, file: File): Uri = ImageStore.uriForFile(context, file)
}

/**
 * Records [content]'s draws into the returned [GraphicsLayer] every frame, in addition
 * to drawing it normally -- so the same composable can be captured to a bitmap
 * (via [ComposeCaptureUtil.captureToPng]) without a second, separately-composed copy.
 * Apply the returned modifier to the root of the composable you intend to capture.
 */
@Composable
fun Modifier.captureGraphicsLayer(layer: GraphicsLayer): Modifier =
    this.drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }

/** Convenience re-export so callers only need one import for the capture-host modifier. */
@Composable
fun rememberCaptureGraphicsLayer(): GraphicsLayer = rememberGraphicsLayer()
