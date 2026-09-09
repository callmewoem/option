package com.habitsfirst.androidclone.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Pure-[zxing-core](https://github.com/zxing/zxing) QR encode/decode for
 * [com.habitsfirst.androidclone.domain.model.HabitType.TAG_SCAN] habits -- no camera
 * preview or live-scanner UI, since completion reuses the same "take a photo" flow every
 * other habit type already has (see `ui/components/PhotoVerificationCapture.kt`); the
 * captured JPEG is decoded once, locally, after the fact.
 */
object QrCode {
    /** Renders [text] as a black-on-white QR bitmap [sizePx] square, or null if it's too long/invalid to encode. */
    fun encode(text: String, sizePx: Int): Bitmap? = try {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Decodes the first QR code found in the JPEG at [path], or null if none is found or the file can't be read. */
    fun decodeFromFile(path: String): String? {
        val bitmap = try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            MultiFormatReader().decode(binaryBitmap).text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }
}
