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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The bedtime curfew cover -- no buttons, pure countdown (design spec §4, §9). Every
 * other lock screen offers some way through (open Locke, take a break, redeem a grace
 * token); curfew deliberately doesn't, because a hard digital curfew that a person
 * mid-craving can talk their way out of isn't a curfew. The only way off this screen is
 * the clock -- or the hardware home button, which still just re-covers on return
 * (design spec §7's "no dismiss by switching apps" mechanic, already true of every
 * block cover).
 */
@Composable
fun CurfewScreen(bedtimeEnd: String) {
    var remainingSeconds by remember(bedtimeEnd) { mutableIntStateOf(secondsUntilBedtimeEnd(bedtimeEnd)) }
    LaunchedEffect(bedtimeEnd) {
        while (true) {
            remainingSeconds = secondsUntilBedtimeEnd(bedtimeEnd)
            delay(1_000)
        }
    }
    // Bedtime windows are short relative to a full day, so the countdown starts
    // (and stays) calm -- it only needs to escalate once genuinely close to over.
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
            text = "BEDTIME",
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

/** Seconds from now until the next occurrence of [endTime] -- today if it hasn't passed yet, tomorrow if the window wraps past midnight. */
private fun secondsUntilBedtimeEnd(endTime: String): Int {
    val end = runCatching { LocalTime.parse(endTime) }.getOrDefault(LocalTime.of(6, 30))
    val now = LocalDateTime.now()
    val endToday = LocalDateTime.of(LocalDate.now(), end)
    val deadline = if (endToday.isAfter(now)) endToday else endToday.plusDays(1)
    return Duration.between(now, deadline).seconds.coerceAtLeast(0L).toInt()
}
