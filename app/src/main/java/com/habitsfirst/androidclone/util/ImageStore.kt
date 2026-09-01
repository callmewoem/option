package com.habitsfirst.androidclone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Copies user-picked photos (habit examples, verification proof) into this app's own
 * storage as downscaled JPEGs -- small enough to send to a vision model cheaply, and
 * independent of whatever transient content:// permission the source picker granted.
 */
object ImageStore {
    private const val EXAMPLE_DIR = "habit_examples"
    private const val VERIFICATION_DIR = "habit_verifications"
    private const val CAPTURE_DIR = "verification_captures"
    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 82

    /** A fresh camera-capture destination: a content:// [Uri] to hand the camera app, and the [File] it writes to. */
    fun createCaptureUri(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }

    /** Saves a habit's example photo, overwriting any previous one for [habitId]. */
    fun saveExampleImage(context: Context, source: Uri, habitId: Long): String? =
        saveScaledJpeg(context, source, File(context.filesDir, EXAMPLE_DIR), "example_$habitId")

    /** Saves a proof photo submitted for verification. */
    fun saveVerificationImage(context: Context, source: Uri, habitId: Long): String? =
        saveScaledJpeg(
            context,
            source,
            File(context.filesDir, VERIFICATION_DIR),
            "verify_${habitId}_${System.currentTimeMillis()}",
        )

    fun readBytes(path: String): ByteArray? =
        try {
            File(path).readBytes()
        } catch (e: IOException) {
            null
        }

    fun deleteQuietly(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }

    private fun saveScaledJpeg(context: Context, source: Uri, dir: File, baseName: String): String? {
        val bitmap = loadScaledBitmap(context, source) ?: return null
        return try {
            dir.mkdirs()
            val file = File(dir, "$baseName.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            file.absolutePath
        } catch (e: IOException) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: IOException) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_DIMENSION * 2 || bounds.outHeight / sampleSize > MAX_DIMENSION * 2) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (e: IOException) {
            null
        } ?: return null

        val longestSide = maxOf(decoded.width, decoded.height)
        if (longestSide <= MAX_DIMENSION) return decoded

        val scale = MAX_DIMENSION.toFloat() / longestSide
        val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        if (scaled != decoded) decoded.recycle()
        return scaled
    }
}
