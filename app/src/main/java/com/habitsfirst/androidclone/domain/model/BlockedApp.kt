package com.habitsfirst.androidclone.domain.model

/**
 * How the app-picker's selected set of packages ([BlockedApp] rows) is applied. Same
 * data, opposite reading -- there's no separate "whitelist" table, just this switch on
 * how [com.habitsfirst.androidclone.service.AppBlockAccessibilityService] reads it.
 */
enum class AppBlockMode {
    /** The selected apps are the ones that get locked; every other app is left alone. */
    BLACKLIST,

    /** The selected apps are always allowed; every other app (bar a small essential-apps exemption) gets locked. */
    WHITELIST,
}

/**
 * An app the user has picked for the block list -- what that means depends on the
 * current [AppBlockMode]: locked (blacklist) or exempt from locking (whitelist).
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
