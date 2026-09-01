package com.habitsfirst.androidclone.ui.habit

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.util.ImageStore
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageVerificationScreen(
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ImageVerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null) {
            viewModel.onImageCaptured(Uri.fromFile(file))
        }
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
    ) { uri -> uri?.let(viewModel::onImageCaptured) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.habit?.name ?: "Verify") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            val habit = state.habit
            if (habit != null && (!habit.verificationPrompt.isNullOrBlank() || habit.verificationExampleImagePath != null)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "What counts as done", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (!habit.verificationPrompt.isNullOrBlank()) {
                            Text(text = habit.verificationPrompt, style = MaterialTheme.typography.bodyMedium)
                        }
                        habit.verificationExampleImagePath?.let { path ->
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = path,
                                contentDescription = "Example photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            val capturedPath = state.capturedImagePath
            if (capturedPath == null) {
                Text(
                    text = "Take a photo (or pick one) that proves you did this today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { launchCamera() }) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take photo")
                    }
                    OutlinedButton(
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose photo")
                    }
                }
            } else {
                AsyncImage(
                    model = capturedPath,
                    contentDescription = "Your proof photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(modifier = Modifier.height(16.dp))

                val result = state.result
                when {
                    state.isVerifying -> {
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
                            OutlinedButton(onClick = viewModel::onRetake) { Text("Retake") }
                            Button(onClick = viewModel::onSubmit) { Text("Try again") }
                        }
                    }
                    result != null && result.approved -> {
                        VerdictCard(approved = true, reasoning = result.reasoning)
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = viewModel::onRetake) { Text("Retake") }
                            Button(onClick = viewModel::onSubmit, modifier = Modifier.fillMaxWidth()) {
                                Text("Submit for verification")
                            }
                        }
                    }
                }

                state.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (state.missingApiKey) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
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
