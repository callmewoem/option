package com.habitsfirst.androidclone.ui.habit

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.ui.components.LockeCard
import com.habitsfirst.androidclone.ui.components.LockePrimaryButton
import com.habitsfirst.androidclone.ui.components.LockeQuietButton
import com.habitsfirst.androidclone.ui.components.NfcReaderModeEffect
import com.habitsfirst.androidclone.ui.components.rememberNfcAvailable
import com.habitsfirst.androidclone.util.ImageStore

/**
 * "Tap your tag, or scan its QR code" -- the completion flow for a
 * [com.habitsfirst.androidclone.domain.model.HabitType.TAG_SCAN] habit. NFC reader mode
 * runs the whole time this screen is open (see [NfcReaderModeEffect]); QR is a one-shot
 * camera capture, same shape as [com.habitsfirst.androidclone.ui.components.PhotoVerificationCapture]
 * but decoded locally instead of sent anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanTagScreen(
    onDone: () -> Unit,
    viewModel: ScanTagViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }
    LaunchedEffect(state.mismatch) {
        if (state.mismatch) {
            snackbarHostState.showSnackbar("That doesn't match this habit's tag.")
            viewModel.onMismatchShown()
        }
    }

    NfcReaderModeEffect(enabled = !state.isDone, onTagDiscovered = viewModel::onNfcTagDiscovered)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.habit?.name ?: "Scan") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (rememberNfcAvailable()) {
                LockeCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Tap your tag", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Hold the back of your phone against it. This screen is listening.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("or", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
            }

            QrScanSection(onImageCaptured = viewModel::onQrImageCaptured)

            Spacer(modifier = Modifier.height(24.dp))
            LockeQuietButton(text = "Mark done anyway", onClick = viewModel::onOverride)
        }
    }
}

@Composable
private fun QrScanSection(onImageCaptured: (Uri) -> Unit) {
    val context = LocalContext.current
    var pendingCaptureFile by remember { mutableStateOf<java.io.File?>(null) }

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

    Text("Scan its QR code", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Take a photo of the QR code you set up for this habit.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))
    LockePrimaryButton(
        text = "Take photo",
        onClick = {
            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                val (uri, file) = ImageStore.createCaptureUri(context)
                pendingCaptureFile = file
                takePictureLauncher.launch(uri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
    )
}
