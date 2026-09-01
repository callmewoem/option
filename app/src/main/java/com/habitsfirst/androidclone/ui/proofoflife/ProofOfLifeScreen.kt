package com.habitsfirst.androidclone.ui.proofoflife

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.ui.components.PhotoVerificationCapture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofOfLifeScreen(
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProofOfLifeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Morning check-in") },
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

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Prove you're up", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "A quick photo of your kitchen, bathroom, or the view outside -- " +
                            "not your bed and not a screen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            PhotoVerificationCapture(
                capturedImagePath = state.capturedImagePath,
                isVerifying = state.isVerifying,
                result = state.result,
                errorMessage = state.errorMessage,
                missingApiKey = state.missingApiKey,
                onImageCaptured = viewModel::onImageCaptured,
                onRetake = viewModel::onRetake,
                onSubmit = viewModel::onSubmit,
                onOpenSettings = onOpenSettings,
                promptText = "Take a photo (or pick one) that proves you're up right now.",
            )
        }
    }
}
