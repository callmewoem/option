package com.habitsfirst.androidclone.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import kotlinx.coroutines.delay

/**
 * "Turn on hard mode" sheet: pick how long the toggle itself locks (a preset, or no time limit at all), and
 * optionally have a friend set a PIN that can end that lock early -- the whole point being that the *user*
 * shouldn't be the one who can just turn hard mode back off on a whim. A lock with no time limit is only
 * offered once a PIN is in place, since without one there'd be no way back in ever (see
 * [PreferencesRepository.setHardModeEnabled]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardModeEnableDialog(
    onConfirm: (lockDurationDays: Int?, pin: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedDays by remember { mutableStateOf<Int?>(PreferencesRepository.HARD_MODE_LOCK_DURATION_PRESETS_DAYS.first()) }
    var pinEnabled by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }

    val isPermanent = selectedDays == null
    // A no-time-limit lock needs a PIN to ever get out of -- force the section open and keep it there.
    LaunchedEffect(isPermanent) { if (isPermanent) pinEnabled = true }

    val pinFilledIn = pin.isNotEmpty() || confirmPin.isNotEmpty()
    val pinValid = pin.length in PreferencesRepository.HARD_MODE_PIN_MIN_LENGTH..PreferencesRepository.HARD_MODE_PIN_MAX_LENGTH &&
        pin == confirmPin
    val canConfirm = if (pinEnabled) pinValid else true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn on hard mode") },
        text = {
            Column {
                Text(
                    text = "Gates and blocked apps can only be added from here, never removed -- and the " +
                        "switch itself locks for as long as you pick below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Lock the switch for", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PreferencesRepository.HARD_MODE_LOCK_DURATION_PRESETS_DAYS.forEach { days ->
                        FilterChip(
                            selected = selectedDays == days,
                            onClick = { selectedDays = days },
                            label = { Text("${days}d") },
                        )
                    }
                    FilterChip(
                        selected = isPermanent,
                        onClick = { selectedDays = null },
                        label = { Text("No time limit") },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Have a friend set a PIN") },
                    supportingContent = {
                        Text(
                            if (isPermanent) {
                                "Required with no time limit -- their PIN becomes the only way back in."
                            } else {
                                "Optional. Lets them let you back in before the lock runs out."
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = pinEnabled,
                            onCheckedChange = { pinEnabled = it },
                            enabled = !isPermanent,
                        )
                    },
                )
                if (pinEnabled) {
                    Text(
                        text = "Hand them your phone and have them type it twice -- look away so you don't see it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PinField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(PreferencesRepository.HARD_MODE_PIN_MAX_LENGTH) },
                        label = "PIN",
                        visible = pinVisible,
                        onVisibleChange = { pinVisible = it },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PinField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it.filter(Char::isDigit).take(PreferencesRepository.HARD_MODE_PIN_MAX_LENGTH)
                        },
                        label = "Confirm PIN",
                        visible = pinVisible,
                        onVisibleChange = { pinVisible = it },
                    )
                    if (pinFilledIn && !pinValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (pin != confirmPin) {
                                "PINs don't match."
                            } else {
                                "At least ${PreferencesRepository.HARD_MODE_PIN_MIN_LENGTH} digits."
                            },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedDays, if (pinEnabled) pin else null) },
                enabled = canConfirm,
            ) { Text("Turn on hard mode") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * "Enter the PIN" sheet, shown when the switch is tapped while hard mode's friend-lock is still active but a
 * PIN was set for it. Unlike [HardModeEnableDialog] this doesn't own the guess/cooldown state itself --
 * [pinResult] comes from [SettingsViewModel.hardModePinResult] so a config change (e.g. rotation) doesn't lose
 * an in-flight cooldown -- but it does own the live countdown display for a cooldown already in progress.
 */
@Composable
fun HardModePinUnlockDialog(
    friendLock: PreferencesRepository.HardModeFriendLock,
    pinResult: PreferencesRepository.HardModePinResult?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }

    // A correct guess is handled by the caller turning hard mode off underneath this dialog -- close it too.
    LaunchedEffect(pinResult) {
        if (pinResult is PreferencesRepository.HardModePinResult.Unlocked) onDismiss()
    }

    val attemptsLockedUntil = when (pinResult) {
        is PreferencesRepository.HardModePinResult.TooManyAttempts -> pinResult.retryAtEpochMillis
        else -> friendLock.pinAttemptsLockedUntilEpochMillis
    }
    var nowMillis by remember(attemptsLockedUntil) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(attemptsLockedUntil) {
        while (attemptsLockedUntil > System.currentTimeMillis()) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
        nowMillis = System.currentTimeMillis()
    }
    val throttled = attemptsLockedUntil > nowMillis

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter the PIN") },
        text = {
            Column {
                Text(
                    text = if (friendLock.isPermanent) {
                        "Hard mode has no time limit -- only the PIN your friend holds can turn it off."
                    } else {
                        "Ask whoever holds the PIN, or just wait out the lock instead."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PinField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(PreferencesRepository.HARD_MODE_PIN_MAX_LENGTH) },
                    label = "PIN",
                    visible = pinVisible,
                    onVisibleChange = { pinVisible = it },
                    enabled = !throttled,
                )
                Spacer(modifier = Modifier.height(4.dp))
                when {
                    throttled -> Text(
                        text = "Too many wrong guesses -- try again in ${formatRetryIn(attemptsLockedUntil, nowMillis)}.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pinResult is PreferencesRepository.HardModePinResult.WrongPin -> Text(
                        text = "Wrong PIN -- ${pinResult.attemptsRemaining} " +
                            "${if (pinResult.attemptsRemaining == 1) "attempt" else "attempts"} left.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pin) }, enabled = pin.isNotEmpty() && !throttled) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { onVisibleChange(!visible) }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide PIN" else "Show PIN",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "3m", "2h", ... time left until [retryAtEpochMillis] from [nowMillis], floored to at least 1 minute while still throttled. */
private fun formatRetryIn(retryAtEpochMillis: Long, nowMillis: Long): String {
    val minutes = ((retryAtEpochMillis - nowMillis) / 60_000L).coerceAtLeast(1L)
    return if (minutes < 60) "${minutes}m" else "${(minutes + 59) / 60}h"
}
