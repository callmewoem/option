package com.habitsfirst.androidclone.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.habitsfirst.androidclone.data.repository.BedtimeRepository
import com.habitsfirst.androidclone.data.repository.BlockedAppRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.LootboxRepository
import com.habitsfirst.androidclone.data.repository.PenaltyRepository
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.ui.block.BlockOverlayActivity
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
 * gating habits, any active penalty lock, the bedtime curfew, and a redeemed grace
 * token that can bypass the first two (never the bedtime curfew).
 */
@AndroidEntryPoint
class AppBlockAccessibilityService : AccessibilityService() {

    @Inject lateinit var habitRepository: HabitRepository

    @Inject lateinit var blockedAppRepository: BlockedAppRepository

    @Inject lateinit var penaltyRepository: PenaltyRepository

    @Inject lateinit var bedtimeRepository: BedtimeRepository

    @Inject lateinit var lootboxRepository: LootboxRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var blockedPackageNames: Set<String> = emptySet()
    private var appUsageTargetPackages: Set<String> = emptySet()
    private var lastForegroundPackage: String? = null
    private var homePackageName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        homePackageName = resolveHomePackageName()
        blockedAppRepository.observeEnabledPackageNames()
            .onEach { blockedPackageNames = it.toSet() }
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
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

        if (packageName !in blockedPackageNames) return

        serviceScope.launch {
            when (val lockState = evaluateLockState()) {
                LockState.Unlocked -> Unit
                is LockState.Locked -> showBlockScreen(packageName, lockState.isBedtime)
            }
        }
    }

    private sealed class LockState {
        data object Unlocked : LockState()
        data class Locked(val isBedtime: Boolean) : LockState()
    }

    private suspend fun evaluateLockState(): LockState {
        if (bedtimeRepository.isWithinBedtimeWindowNow()) return LockState.Locked(isBedtime = true)

        val graceActive = lootboxRepository.isGraceUnlockActive()
        if (graceActive) return LockState.Unlocked

        val habitsComplete = habitRepository.areAllHabitsCompletedForDate()
        val penaltyActive = penaltyRepository.isPenaltyLockActive()
        return if (!habitsComplete || penaltyActive) LockState.Locked(isBedtime = false) else LockState.Unlocked
    }

    private fun resolveHomePackageName(): String? {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun showBlockScreen(blockedPackageName: String, isBedtime: Boolean) {
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE_NAME, blockedPackageName)
            putExtra(BlockOverlayActivity.EXTRA_IS_BEDTIME, isBedtime)
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
    }
}
