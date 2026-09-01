package com.habitsfirst.androidclone.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * The "take/pick a photo, submit it, show the vision-model verdict" flow shared by every
 * photo-verification surface in the app -- a habit's daily proof photo and the proof-of-life
 * check-in alike. Screens own their own [android.net.Uri]-to-bytes plumbing and what
 * "approved" means; this composable only owns the capture UI and result rendering.
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
    promptText: String = "Take a photo (or pick one) that proves you did this today.",
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
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onImageCaptured) }

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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { launchCamera() }) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Take photo")
            }
            OutlinedButton(
                onClick = {
                    pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose photo")
            }
        }
    } else {
        AsyncImage(
            model = capturedImagePath,
            contentDescription = "Your proof photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp)),
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
                    OutlinedButton(onClick = onRetake) { Text("Retake") }
                    Button(onClick = onSubmit) { Text("Try again") }
                }
            }
            result != null && result.approved -> {
                VerdictCard(approved = true, reasoning = result.reasoning)
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRetake) { Text("Retake") }
                    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
                        Text("Submit for verification")
                    }
                }
            }
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            if (missingApiKey) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
            }
        }
    }
}

@Composable
private fun VerdictCard(approved: Boolean, reasoning: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (approved) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (approved) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (approved) "Verified!" else "Not quite",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(text = reasoning, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
