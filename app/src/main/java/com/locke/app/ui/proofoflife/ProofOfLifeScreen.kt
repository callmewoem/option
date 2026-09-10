package com.locke.app.ui.proofoflife

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locke.app.ui.components.BigNumber
import com.locke.app.ui.components.LockeQuietButton
import com.locke.app.ui.components.PhotoVerificationCapture
import com.locke.app.ui.components.countdownColor
import com.locke.app.ui.components.formatCountdown
import com.locke.app.ui.theme.LockeColor
import com.locke.app.ui.theme.LockeMode
import com.locke.app.ui.theme.LockeTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The morning check-in lock -- stricter than every other enforcement screen (design
 * spec §7). Today's Home banner (design spec §8) is the same countdown at a glance;
 * this is the full takeover it points to. The countdown is the single largest number
 * anywhere in the app and nothing else competes with it -- this is also the visual
 * pattern the eventual device-wide takeover (covering literally everything except
 * calls and alarms, not just this app) reuses; that device-wide interception itself is
 * a system-service concern outside this screen.
 */
@Composable
fun ProofOfLifeScreen(
    onDone: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: ProofOfLifeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    LockeTheme(mode = LockeMode.Enforcement) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                CheckInCountdown(deadlineTime = state.deadlineTime, windowMinutes = state.windowMinutes)
                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "A photo proving you're up -- your kitchen, bathroom, or the view outside. " +
                        "Not your bed, not a screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LockeColor.OnIronMuted,
                )
                Spacer(modifier = Modifier.height(20.dp))

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
                    promptText = "Take a photo that proves you're up right now.",
                    freeChecksRemaining = state.freeChecksRemaining,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // The honest exit, always present, never hidden behind a gesture
                // (design spec §4, §6.2): priced in the same sentence it's offered.
                // Below the fold rather than weight-pinned to the bottom, since this
                // column now scrolls -- weight() requires a bounded-height parent.
                LockeQuietButton(
                    text = "Take the penalty now (+${state.penaltyMinutes} min lock) instead of waiting",
                    onClick = viewModel::onTakePenaltyNow,
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = LockeColor.OnIronMuted,
                )
            }
        }
    }
}

@Composable
private fun CheckInCountdown(deadlineTime: String, windowMinutes: Int) {
    var remainingSeconds by remember(deadlineTime, windowMinutes) {
        mutableIntStateOf(secondsUntilDeadline(deadlineTime, windowMinutes))
    }
    LaunchedEffect(deadlineTime, windowMinutes) {
        while (true) {
            remainingSeconds = secondsUntilDeadline(deadlineTime, windowMinutes)
            delay(1_000)
        }
    }
    val totalWindowSeconds = (windowMinutes * 60).coerceAtLeast(1)
    val isPast = remainingSeconds <= 0
    val fractionElapsed = 1f - (remainingSeconds.toFloat() / totalWindowSeconds).coerceAtLeast(0f)
    val color = countdownColor(fractionElapsed = fractionElapsed, isPastDeadline = isPast)

    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = "MORNING CHECK-IN",
            style = MaterialTheme.typography.labelLarge,
            color = LockeColor.OnIronMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        BigNumber(
            value = formatCountdown(remainingSeconds),
            caption = if (isPast) "past deadline -- the penalty has landed" else "left to check in",
            color = color,
            captionColor = LockeColor.OnIronMuted,
            size = 72.sp,
        )
    }
}

private fun secondsUntilDeadline(deadlineTime: String, windowMinutes: Int): Int {
    val target = runCatching { LocalTime.parse(deadlineTime) }.getOrDefault(LocalTime.of(8, 0))
    val deadline = LocalDateTime.of(LocalDate.now(), target).plusMinutes(windowMinutes.toLong())
    val seconds = Duration.between(LocalDateTime.now(), deadline).seconds
    return seconds.coerceAtLeast(0L).toInt()
}
