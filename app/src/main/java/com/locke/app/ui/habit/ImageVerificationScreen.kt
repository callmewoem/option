package com.locke.app.ui.habit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.locke.app.R
import com.locke.app.ui.components.LockeCard
import com.locke.app.ui.components.PhotoVerificationCapture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageVerificationScreen(
    onDone: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: ImageVerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
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
                LockeCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                                    .clip(MaterialTheme.shapes.medium),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            PhotoVerificationCapture(
                capturedImagePath = state.capturedImagePath,
                isVerifying = state.isVerifying,
                result = state.result,
                errorMessage = state.errorMessage,
                requiresPremium = state.requiresPremium,
                onImageCaptured = viewModel::onImageCaptured,
                onRetake = viewModel::onRetake,
                onSubmit = viewModel::onSubmit,
                onUpgrade = onUpgrade,
                onOverride = viewModel::onOverride,
                freeChecksRemaining = state.freeChecksRemaining,
            )
        }
    }
}
