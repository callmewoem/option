package com.habitsfirst.androidclone.ui.components

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** Whether this device has NFC hardware at all -- [com.habitsfirst.androidclone.domain.model.HabitType.TAG_SCAN]'s NFC path hides itself when false (QR still works everywhere). */
@Composable
fun rememberNfcAvailable(): Boolean {
    val context = LocalContext.current
    return NfcAdapter.getDefaultAdapter(context) != null
}

/**
 * Enables NFC reader mode on the current foreground [Activity] while [enabled] is true,
 * invoking [onTagDiscovered] for every tag presented -- shared by
 * [com.habitsfirst.androidclone.domain.model.HabitType.TAG_SCAN]'s "write to my tag"
 * setup step and its "tap to confirm" completion screen. [onTagDiscovered] fires on a
 * binder thread, not the main thread (see [NfcAdapter.enableReaderMode]'s own contract) --
 * callers should only touch thread-safe state from it (a `StateFlow`/`MutableState`
 * write, or a coroutine launch), never touch a `View` or run other Compose-only work
 * directly.
 */
@Composable
fun NfcReaderModeEffect(enabled: Boolean, onTagDiscovered: (Tag) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val currentOnTagDiscovered by rememberUpdatedState(onTagDiscovered)

    DisposableEffect(enabled, activity) {
        val adapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        if (enabled && activity != null && adapter != null) {
            adapter.enableReaderMode(
                activity,
                { tag -> currentOnTagDiscovered(tag) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null,
            )
        }
        onDispose {
            if (activity != null && adapter != null) {
                runCatching { adapter.disableReaderMode(activity) }
            }
        }
    }
}
