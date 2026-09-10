package com.locke.app.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locke.app.ui.components.BigNumber
import com.locke.app.ui.components.countdownColor
import com.locke.app.ui.components.formatCountdown
import com.locke.app.ui.theme.LockeColor
import kotlinx.coroutines.delay

/**
 * The self-lockout cover -- same "no buttons, pure countdown" treatment as
 * [CurfewScreen] (design spec §4, §9), and for the same reason: a self-imposed lockout
 * that a person mid-craving can talk their way out of from inside the very app it's
 * meant to keep them out of isn't a lockout. The only way off this screen is the clock,
 * or deliberately going back to Locke's own Home to end it early -- see
 * [com.locke.app.data.repository.LockoutRepository].
 */
@Composable
fun LockoutScreen(lockoutUntilEpochMillis: Long) {
    var remainingSeconds by remember(lockoutUntilEpochMillis) {
        mutableIntStateOf(secondsUntilLockoutEnd(lockoutUntilEpochMillis))
    }
    LaunchedEffect(lockoutUntilEpochMillis) {
        while (true) {
            remainingSeconds = secondsUntilLockoutEnd(lockoutUntilEpochMillis)
            delay(1_000)
        }
    }
    // Lockouts are short relative to a full day, same as bedtime windows -- the
    // countdown starts (and stays) calm, only escalating once genuinely close to over.
    val fractionElapsed = if (remainingSeconds <= FINAL_STRETCH_SECONDS) 0.9f else 0.2f
    val color = countdownColor(fractionElapsed = fractionElapsed, isPastDeadline = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "LOCKOUT",
            style = MaterialTheme.typography.labelLarge,
            color = LockeColor.OnIronMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        BigNumber(
            value = formatCountdown(remainingSeconds),
            caption = "until locked apps unlock",
            color = color,
            captionColor = LockeColor.OnIronMuted,
            size = 64.sp,
        )
    }
}

private const val FINAL_STRETCH_SECONDS = 15 * 60

/** Seconds from now until [targetEpochMillis]; 0 once past. */
private fun secondsUntilLockoutEnd(targetEpochMillis: Long): Int =
    ((targetEpochMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L).toInt()
