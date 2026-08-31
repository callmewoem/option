package com.habitsfirst.androidclone.domain.model

/**
 * An app the user has chosen to keep locked until today's habits are complete.
 */
data class BlockedApp(
    val packageName: String,
    val appLabel: String,
    val isEnabled: Boolean = true,
    val addedAtEpochMillis: Long = System.currentTimeMillis(),
)

/** Lightweight description of any app installed on the device, for the app picker. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)
