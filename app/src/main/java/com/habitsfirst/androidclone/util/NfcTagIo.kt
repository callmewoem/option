package com.habitsfirst.androidclone.util

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException
import java.nio.charset.Charset

/**
 * Reads/writes a plain NDEF text record for [com.habitsfirst.androidclone.domain.model.HabitType.TAG_SCAN]
 * habits -- the tag just carries this habit's random [com.habitsfirst.androidclone.domain.model.Habit.tagPayload]
 * string, nothing app-specific, so any blank writable tag works.
 */
object NfcTagIo {
    private const val LANGUAGE_CODE = "en"

    /**
     * Writes [text] to [tag] as its sole NDEF record, formatting it first if it's a
     * blank tag with no NDEF data yet. Returns false (never throws) if the tag isn't
     * writable, is too small, or was pulled away mid-write.
     */
    fun writeText(tag: Tag, text: String): Boolean {
        val message = NdefMessage(arrayOf(NdefRecord.createTextRecord(LANGUAGE_CODE, text)))

        Ndef.get(tag)?.let { ndef ->
            return try {
                ndef.connect()
                if (!ndef.isWritable || ndef.maxSize < message.byteArrayLength) return false
                ndef.writeNdefMessage(message)
                true
            } catch (e: IOException) {
                false
            } catch (e: FormatException) {
                false
            } catch (e: TagLostException) {
                false
            } finally {
                runCatching { ndef.close() }
            }
        }

        val formatable = NdefFormatable.get(tag) ?: return false
        return try {
            formatable.connect()
            formatable.format(message)
            true
        } catch (e: IOException) {
            false
        } catch (e: FormatException) {
            false
        } catch (e: TagLostException) {
            false
        } finally {
            runCatching { formatable.close() }
        }
    }

    /** Reads the first NDEF text record's payload text off [tag], or null if it has none (or isn't NDEF at all). */
    fun readText(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        val message = try {
            ndef.connect()
            ndef.ndefMessage ?: ndef.cachedNdefMessage
        } catch (e: IOException) {
            null
        } catch (e: FormatException) {
            null
        } catch (e: TagLostException) {
            null
        } finally {
            runCatching { ndef.close() }
        } ?: return null

        val record = message.records.firstOrNull {
            it.tnf == NdefRecord.TNF_WELL_KNOWN && it.type.contentEquals(NdefRecord.RTD_TEXT)
        } ?: return null
        return parseTextPayload(record.payload)
    }

    /**
     * NDEF text record payload format (RTD_TEXT): byte 0 is a status byte -- bit 7 is the
     * text encoding (0 = UTF-8, 1 = UTF-16), bits 5:0 are the following language code's
     * byte length (ignored here, just skipped over) -- everything after that is the text
     * itself in the indicated encoding.
     */
    private fun parseTextPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        return try {
            val statusByte = payload[0].toInt()
            val isUtf16 = (statusByte and 0x80) != 0
            val languageCodeLength = statusByte and 0x3F
            val textStart = 1 + languageCodeLength
            if (textStart > payload.size) return null
            val charset = if (isUtf16) Charset.forName("UTF-16") else Charsets.UTF_8
            String(payload, textStart, payload.size - textStart, charset)
        } catch (e: Exception) {
            null
        }
    }
}
