package com.habitsfirst.androidclone.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.habitsfirst.androidclone.data.verification.VerificationResult
import com.habitsfirst.androidclone.util.ImageStore
import java.io.File

/**
 * The "take a photo, submit it, show the vision-model verdict" flow shared by every
 * photo-verification surface in the app -- a habit's daily proof photo, the morning
 * check-in lock alike. Camera-only by design (see the comment at the capture button) --
 * no gallery picker, so a stored photo can't stand in for today's proof.
 *
 * Any automated rejection carries a visible "I did this -- mark it done anyway" path
 * (design spec §6.2): automated judgment is always overridable, never a dead end.
 * [onOverride] is null when no override is available for this surface.
 */
@Composable
fun PhotoVerificationCapture(
    capturedImagePath: String?,
    isVerifying: Boolean,
    result: VerificationResult?,
    errorMessage: String?,
    missingApiKey: Boolean,
    onImageCaptured: (Uri) -> Unit,
    onRetake: () -> Unit,
    onSubmit: () -> Unit,
    onOpenSettings: () -> Unit,
    onOverride: (() -> Unit)? = null,
    promptText: String = "Take a photo that proves you did this today.",
) {
    val context = LocalContext.current
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null) onImageCaptured(Uri.fromFile(file))
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val (uri, file) = ImageStore.createCaptureUri(context)
            pendingCaptureFile = file
            takePictureLauncher.launch(uri)
        }
    }
    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val (uri, file) = ImageStore.createCaptureUri(context)
            pendingCaptureFile = file
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (capturedImagePath == null) {
        Text(text = promptText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        // Camera-only, deliberately: a gallery picker would let an old or unrelated photo
        // stand in for today's proof, defeating the point of verification.
        LockePrimaryButton(
            text = "Take photo",
            onClick = { launchCamera() },
            leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
        )
    } else {
        AsyncImage(
            model = capturedImagePath,
            contentDescription = "Your proof photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            isVerifying -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Checking your photo…")
                }
            }
            result != null && !result.approved -> {
                VerdictCard(approved = false, reasoning = result.reasoning)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LockeGhostButton(text = "Retake", onClick = onRetake)
                    LockePrimaryButton(text = "Try again", onClick = onSubmit)
                }
                if (onOverride != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LockeQuietButton(text = "I did this -- mark it done anyway", onClick = onOverride)
                }
            }
            result != null && result.approved -> {
                VerdictCard(approved = true, reasoning = result.reasoning)
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LockeGhostButton(text = "Retake", onClick = onRetake)
                    LockePrimaryButton(text = "Submit for verification", onClick = onSubmit, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            if (missingApiKey) {
                Spacer(modifier = Modifier.height(8.dp))
                LockeGhostButton(text = "Open Settings", onClick = onOpenSettings)
            } else if (onOverride != null) {
                // A failure that isn't a clean rejection (network error, etc.) still
                // deserves the same override, not just a dead end.
                Spacer(modifier = Modifier.height(8.dp))
                LockeQuietButton(text = "I did this -- mark it done anyway", onClick = onOverride)
            }
        }
    }
}

@Composable
private fun VerdictCard(approved: Boolean, reasoning: String) {
    val accent = if (approved) MaterialTheme.colorScheme.primary else com.habitsfirst.androidclone.ui.theme.LockeColor.Oxide
    LockeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (approved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        borderColor = accent,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (approved) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = accent,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (approved) "Verified" else "Not verified",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                )
                // Failure/rejection states state the specific fact, not a score or a
                // vague "try again" (design spec §5) -- reasoning is the model's own
                // stated reason, shown verbatim.
                Text(text = reasoning, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
