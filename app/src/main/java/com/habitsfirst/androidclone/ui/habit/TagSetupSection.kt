package com.habitsfirst.androidclone.ui.habit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.ui.components.LockeGhostButton
import com.habitsfirst.androidclone.ui.components.NfcReaderModeEffect
import com.habitsfirst.androidclone.ui.components.rememberNfcAvailable
import com.habitsfirst.androidclone.util.NfcTagIo
import com.habitsfirst.androidclone.util.QrCode

/**
 * Setup for a [com.habitsfirst.androidclone.domain.model.HabitType.TAG_SCAN] habit: show
 * (and write) the QR/NFC payload generated once for this habit -- see
 * [AddEditHabitViewModel.onTypeChanged]. Either method, or both, confirms it later from
 * `ScanTagScreen`.
 */
@Composable
fun TagSetupSection(payload: String) {
    var showQrDialog by remember { mutableStateOf(false) }
    var isWritingMode by remember { mutableStateOf(false) }
    var writeResult by remember { mutableStateOf<Boolean?>(null) }
    val nfcAvailable = rememberNfcAvailable()

    // Runs only while isWritingMode is true, i.e. only after the user explicitly taps
    // "Write to NFC tag" below -- see NfcReaderModeEffect's own thread-safety note for
    // why setting Compose state directly from the callback is fine.
    NfcReaderModeEffect(enabled = isWritingMode) { tag ->
        writeResult = NfcTagIo.writeText(tag, payload)
        isWritingMode = false
    }

    Text("Set up your tag", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Print or display a QR code at the spot, write it to a spare NFC tag, or both. " +
            "Either one confirms this habit later.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LockeGhostButton(text = "Show QR code", onClick = { showQrDialog = true })
        if (nfcAvailable) {
            LockeGhostButton(
                text = if (isWritingMode) "Waiting for tag…" else "Write to NFC tag",
                enabled = !isWritingMode,
                onClick = { writeResult = null; isWritingMode = true },
            )
        }
    }
    if (isWritingMode) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Hold a blank NFC tag to the back of your phone…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    writeResult?.let { success ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (success) "Tag written." else "Couldn't write to that tag. Try a different one.",
            style = MaterialTheme.typography.bodySmall,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }

    if (showQrDialog) {
        val bitmap = remember(payload) { QrCode.encode(payload, 512) }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("Your QR code") },
            text = {
                Column {
                    Text(
                        "Screenshot or print this and place it at the target spot.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR code for this habit",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Couldn't generate a QR code.", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) { Text("Done") }
            },
        )
    }
}
