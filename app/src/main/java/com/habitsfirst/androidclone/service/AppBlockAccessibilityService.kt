package com.habitsfirst.androidclone.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import com.habitsfirst.androidclone.data.repository.ActiveDomainBlock
import com.habitsfirst.androidclone.data.repository.BedtimeRepository
import com.habitsfirst.androidclone.data.repository.BlockAttemptRepository
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.LimitedUnblockRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.data.repository.PenaltyRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.data.repository.UrlBlockRepository
import com.habitsfirst.androidclone.domain.model.AppBlockMode
import com.habitsfirst.androidclone.domain.model.BlockMode
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.ui.block.BlockOverlayActivity
import com.habitsfirst.androidclone.util.BrowserUrlExtractor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Watches for foreground-app changes and, if the app the user just switched to is on
 * their block list and it isn't currently allowed to be open, immediately covers it
 * with [BlockOverlayActivity]. This is the same "accessibility event -> overlay
 * activity" technique most Play Store app-blockers use, since Android has no public
 * API to stop an app from launching outright.
 *
 * "Allowed to be open" now has more than one gate (see [evaluateLockState]): today's
 * gating habits, any active penalty lock, optionally [LimitedUnblockRepository]'s
 * post-completion window, the bedtime curfew, and a redeemed grace token that can
 * bypass all but the bedtime curfew.
 *
 * The same overlay technique also covers URL blocking: when a known browser is
 * foreground, [handleBrowserUrlChanged] reads its address bar (see
 * [BrowserUrlExtractor]) and, if the host matches an enabled block list, covers the
 * browser too -- gated the same way as apps, or unconditionally if that list is
 * [BlockMode.PERMANENT]. Unlike an app, a browser has no per-tab lock on Android --
 * only per-app -- so before covering it, a [GLOBAL_ACTION_BACK] steps the active tab
 * off the blocked page first. Otherwise the tab is left parked there and the *whole*
 * browser re-covers itself the instant it's foregrounded again, for any reason,
 * effectively hard-locking it rather than just that one navigation.
 *
 * App blocking has two readings of the same selected-package set, per
 * [AppBlockMode]: [AppBlockMode.BLACKLIST] (default) locks the selected apps and
 * leaves everything else alone; [AppBlockMode.WHITELIST] flips that -- the selected
 * apps are always allowed and every other app gets locked, short of a small
 * always-exempt set ([resolveEssentialPackageNames]) so the device stays usable
 * (dialer, default messaging, Settings, the launcher, this app itself).
 */
@AndroidEntryPoint
class AppBlockAccessibilityService : AccessibilityService() {

    @Inject lateinit var habitRepository: HabitRepository

    @Inject lateinit var blockedAppRepository: BlockedAppRepository

    @Inject lateinit var penaltyRepository: PenaltyRepository

    @Inject lateinit var bedtimeRepository: BedtimeRepository

    @Inject lateinit var lootboxRepository: LootboxRepository

    @Inject lateinit var limitedUnblockRepository: LimitedUnblockRepository

    @Inject lateinit var urlBlockRepository: UrlBlockRepository

    @Inject lateinit var preferencesRepository: PreferencesRepository

    @Inject lateinit var blockAttemptRepository: BlockAttemptRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The app picker's selected packages -- locked (blacklist) or exempt (whitelist) depending on [appBlockMode]. */
    private var selectedPackageNames: Set<String> = emptySet()
    private var appBlockMode: AppBlockMode = AppBlockMode.BLACKLIST
    private var appUsageTargetPackages: Set<String> = emptySet()
    private var activeDomainIndex: Map<String, ActiveDomainBlock> = emptyMap()
    private var lastForegroundPackage: String? = null
    private var homePackageName: String? = null
    private var essentialPackageNames: Set<String> = emptySet()
    private val lastUrlCheckElapsedMs = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        homePackageName = resolveHomePackageName()
        essentialPackageNames = resolveEssentialPackageNames()
        blockedAppRepository.observeEnabledPackageNames()
            .onEach { selectedPackageNames = it.toSet() }
            .launchIn(serviceScope)
        preferencesRepository.appBlockMode
            .onEach { appBlockMode = it }
            .launchIn(serviceScope)
        habitRepository.observeHabits()
            .map { habits ->
                habits
                    .filter { it.type == HabitType.APP_USAGE_MINUTES }
                    .mapNotNull { it.targetPackageName }
                    .toSet()
            }
            .onEach { appUsageTargetPackages = it }
            .launchIn(serviceScope)
        urlBlockRepository.observeActiveDomainIndex()
            .onEach { activeDomainIndex = it }
            .launchIn(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // Content-changed events fire for every app system-wide (no android:packageNames
        // filter in accessibility_service_config.xml -- app-usage tracking below needs
        // *every* app's window-state changes, which rules out a static filter). Bail
        // immediately for the overwhelming majority that aren't a known browser; a
        // known browser's own event rate is already coalesced by notificationTimeout.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleBrowserUrlChanged(packageName)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // A fresh browser window (tab switch, cold launch) won't fire a content-changed
        // event on its own -- check it here too, before the lastForegroundPackage dedup
        // below (which is keyed for the app-block path, not URL matching).
        handleBrowserUrlChanged(packageName)

        if (packageName == lastForegroundPackage) return

        val previousPackage = lastForegroundPackage
        lastForegroundPackage = packageName

        if (packageName == applicationContext.packageName) return
        if (packageName == homePackageName) return
        if (IGNORED_PACKAGE_PREFIXES.any { packageName.startsWith(it) }) return

        // The user just left an app-usage habit's target app -- refresh its progress
        // sooner than the next 15-minute periodic tick.
        if (previousPackage != null && previousPackage in appUsageTargetPackages) {
            WorkScheduler.requestUsageRefreshNow(applicationContext)
        }

        val isTargetedByBlockMode = when (appBlockMode) {
            AppBlockMode.BLACKLIST -> packageName in selectedPackageNames
            AppBlockMode.WHITELIST -> packageName !in selectedPackageNames && packageName !in essentialPackageNames
        }
        if (!isTargetedByBlockMode) return

        serviceScope.launch {
            when (val lockState = evaluateLockState()) {
                LockState.Unlocked -> Unit
                is LockState.Locked -> {
                    blockAttemptRepository.logAttempt(packageName)
                    showAppBlockScreen(packageName, lockState.isBedtime, lockState.habitsCompleteButLocked)
                }
            }
        }
    }

    /** Reads [packageName]'s address bar (if it's a known browser) and covers it when the host matches an enabled block list. */
    private fun handleBrowserUrlChanged(packageName: String) {
        if (packageName !in BrowserUrlExtractor.KNOWN_BROWSER_PACKAGES) return
        if (activeDomainIndex.isEmpty()) return

        val now = SystemClock.elapsedRealtime()
        val last = lastUrlCheckElapsedMs[packageName] ?: 0L
        if (now - last < URL_CHECK_THROTTLE_MS) return
        lastUrlCheckElapsedMs[packageName] = now

        val addressBarText = BrowserUrlExtractor.findCurrentUrl(rootInActiveWindow, packageName) ?: return
        val host = BrowserUrlExtractor.extractHost(addressBarText) ?: return
        val block = UrlBlockRepository.findBlockForHost(host, activeDomainIndex) ?: return

        serviceScope.launch {
            val lockState = if (block.blockMode == BlockMode.PERMANENT) {
                LockState.Locked(isBedtime = false, isPermanent = true)
            } else {
                evaluateLockState()
            }
            if (lockState is LockState.Locked) {
                // Step the browser's *active tab* off the blocked page before covering it.
                // Without this, the tab is left sitting on the blocked host, so simply
                // backgrounding the overlay (Home, or Open Habits) doesn't actually escape
                // it -- the next time this browser becomes foreground for *any* reason
                // (switching back to it, even to reach a different tab), the address bar
                // still reads the blocked host and the whole app gets covered again. That
                // reads as the entire browser being hard-locked rather than just this one
                // navigation, since there's no per-tab block on Android, only per-app.
                // A back action only affects the current tab's history, so other tabs are
                // untouched.
                performGlobalAction(GLOBAL_ACTION_BACK)
                blockAttemptRepository.logAttempt(host)
                showUrlBlockScreen(
                    host,
                    block.listName,
                    lockState.isPermanent,
                    lockState.isBedtime,
                    lockState.habitsCompleteButLocked,
                )
            }
        }
    }

    private sealed class LockState {
        data object Unlocked : LockState()

        data class Locked(
            val isBedtime: Boolean,
            val isPermanent: Boolean = false,
            /**
             * True when this lock kicked in *despite* today's gating habits already
             * being complete -- an active penalty, or [LimitedUnblockRepository]'s
             * post-completion window running out.
             * [com.habitsfirst.androidclone.ui.block.BlockOverlayViewModel] needs this
             * to stop the block screen from auto-dismissing itself the instant it sees
             * [HabitRepository.areAllHabitsCompletedForDate] read true, which is
             * otherwise exactly what's happening here.
             */
            val habitsCompleteButLocked: Boolean = false,
        ) : LockState()
    }

    private suspend fun evaluateLockState(): LockState {
        if (bedtimeRepository.isWithinBedtimeWindowNow()) return LockState.Locked(isBedtime = true)
        if (lootboxRepository.isGraceUnlockActive()) return LockState.Unlocked

        if (!habitRepository.areAllHabitsCompletedForDate()) return LockState.Locked(isBedtime = false)
        if (penaltyRepository.isPenaltyLockActive()) {
            return LockState.Locked(isBedtime = false, habitsCompleteButLocked = true)
        }

        return if (limitedUnblockRepository.isWithinUnlockWindow()) {
            LockState.Unlocked
        } else {
            LockState.Locked(isBedtime = false, habitsCompleteButLocked = true)
        }
    }

    private fun resolveHomePackageName(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    /**
     * Packages [AppBlockMode.WHITELIST] never locks, whatever the user did or didn't pick,
     * so turning it on can't strand them without a way back to a phone call, a text, or
     * Settings to fix their selection. The device's actual default dialer/SMS handler are
     * resolved live (OEM builds vary); [ESSENTIAL_PACKAGE_NAME_FALLBACKS] covers Settings
     * and those defaults' most common package names in case that lookup comes back empty.
     */
    private fun resolveEssentialPackageNames(): Set<String> {
        val essential = mutableSetOf<String>()
        runCatching {
            (getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
        }.getOrNull()?.let { essential += it }
        runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()?.let { essential += it }
        essential += ESSENTIAL_PACKAGE_NAME_FALLBACKS
        return essential
    }

    private fun showAppBlockScreen(blockedPackageName: String, isBedtime: Boolean, habitsCompleteButLocked: Boolean) {
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockOverlayActivity.EXTRA_IS_URL_BLOCK, false)
            putExtra(BlockOverlayActivity.EXTRA_TARGET, blockedPackageName)
            putExtra(BlockOverlayActivity.EXTRA_IS_BEDTIME, isBedtime)
            putExtra(BlockOverlayActivity.EXTRA_HABITS_COMPLETE_BUT_LOCKED, habitsCompleteButLocked)
        }
        startActivity(intent)
    }

    private fun showUrlBlockScreen(
        domain: String,
        listName: String,
        isPermanent: Boolean,
        isBedtime: Boolean,
        habitsCompleteButLocked: Boolean,
    ) {
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockOverlayActivity.EXTRA_IS_URL_BLOCK, true)
            putExtra(BlockOverlayActivity.EXTRA_TARGET, domain)
            putExtra(BlockOverlayActivity.EXTRA_LIST_NAME, listName)
            putExtra(BlockOverlayActivity.EXTRA_IS_PERMANENT, isPermanent)
            putExtra(BlockOverlayActivity.EXTRA_IS_BEDTIME, isBedtime)
            putExtra(BlockOverlayActivity.EXTRA_HABITS_COMPLETE_BUT_LOCKED, habitsCompleteButLocked)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // No-op: nothing to tear down.
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        /** System UI / launcher-ish packages we should never try to cover with the block screen. */
        private val IGNORED_PACKAGE_PREFIXES = listOf(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
        )

        /** Minimum gap between two address-bar reads for the same browser -- content-changed events can fire in quick bursts (e.g. a loading page). */
        private const val URL_CHECK_THROTTLE_MS = 250L

        /** Best-effort backstop for [resolveEssentialPackageNames] -- AOSP's own package names, which most OEMs keep as-is even when they ship a differently-named default dialer/messaging app. */
        private val ESSENTIAL_PACKAGE_NAME_FALLBACKS = setOf(
            "com.android.settings",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.messaging",
            "com.google.android.apps.messaging",
        )
    }
}
