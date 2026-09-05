package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional alternative to the default "blocked apps stay unlocked for the rest of the
 * day once today's gating habits are done" behavior (see
 * [com.habitsfirst.androidclone.service.AppBlockAccessibilityService]): while enabled,
 * that unlock only lasts [PreferencesRepository.LimitedUnblockWindowSettings.windowMinutes]
 * (configurable in Settings, see [PreferencesRepository.limitedUnblockWindowSettings])
 * before blocked apps re-lock, even though the habits are (and stay) complete for the
 * rest of the day. Optionally, that window is stretched by
 * [PreferencesRepository.LimitedUnblockWindowSettings.streakBonusMinutesPerDay] minutes
 * for every day of the user's current streak (see [HabitRepository.computeCurrentStreak]),
 * up to [PreferencesRepository.MAX_LIMITED_UNBLOCK_WINDOW_MINUTES] total -- a small reward
 * for consistency that still can't stretch the window out indefinitely. Never bypasses
 * bedtime or a permanent URL
 * block, same as habit gating itself; a grace token still bypasses it, same as it
 * bypasses habit gating.
 *
 * The window's start is stamped lazily -- the first time [isWithinUnlockWindow] sees a
 * date it hasn't stamped yet -- rather than at the exact instant a habit gets marked
 * done. Habit completion can happen from more than one place (Home's own actions, but
 * also the app-usage and Health Connect background workers), so there's no single call
 * site to hook reliably; this mirrors how [LootboxRepository.maybeAwardDailyLootbox]
 * already guards its own once-per-day state.
 *
 * [isWithinUnlockWindow] runs on [com.habitsfirst.androidclone.service.AppBlockAccessibilityService]'s
 * hot path -- once per foreground app switch -- so the streak bonus doesn't call
 * [HabitRepository.computeCurrentStreak] (an O(streak length) walk over Room) on every
 * call; it's read through [PreferencesRepository.cachedStreak] instead, which is only
 * ever stale for one calendar day at a time and gets refreshed here at most once per day.
 */
@Singleton
class LimitedUnblockRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val habitRepository: HabitRepository,
) {
    val isEnabled: Flow<Boolean> = preferencesRepository.isLimitedUnblockEnabled

    suspend fun setEnabled(enabled: Boolean) {
        preferencesRepository.setLimitedUnblockEnabled(enabled)
    }

    /**
     * True if blocked apps should stay unlocked -- either the feature is off, or
     * today's window hasn't run out yet. Only meaningful once the caller has already
     * confirmed habits are complete and no penalty lock is active; stamps today's
     * window start on its first call each day.
     */
    suspend fun isWithinUnlockWindow(
        date: String = DateProvider.todayString(),
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!preferencesRepository.isLimitedUnblockEnabled.first()) return true
        val startedAt = stampWindowStartIfNeeded(date, nowEpochMillis)
        return nowEpochMillis < startedAt + effectiveWindowMinutes(date) * 60_000L
    }

    /** Returns today's window start, stamping it as [nowEpochMillis] if this is the first call for [date]. */
    private suspend fun stampWindowStartIfNeeded(date: String, nowEpochMillis: Long): Long {
        if (preferencesRepository.habitsCompleteUnlockWindowDate.first() == date) {
            return preferencesRepository.habitsCompleteUnlockWindowStartedAtEpochMillis.first()
        }
        preferencesRepository.stampHabitsCompleteUnlockWindowStart(date, nowEpochMillis)
        return nowEpochMillis
    }

    /** Today's configured base window, plus the streak bonus if that's enabled -- see [computeEffectiveWindowMinutes]. */
    private suspend fun effectiveWindowMinutes(date: String): Int {
        val settings = preferencesRepository.limitedUnblockWindowSettings.first()
        val bonusMinutes = if (settings.streakBonusEnabled) {
            currentStreakCached(date) * settings.streakBonusMinutesPerDay
        } else {
            0
        }
        return computeEffectiveWindowMinutes(settings.windowMinutes, bonusMinutes)
    }

    /**
     * [HabitRepository.computeCurrentStreak] walks every day of the streak against Room,
     * so it's only run once per [date] and cached in [PreferencesRepository.cachedStreak]
     * for the rest of that day's calls -- this is the hot path described in the class doc.
     * Safe to cache per-day here specifically because [isWithinUnlockWindow] is only ever
     * meaningful once today's habits are already complete, so today's streak can't change
     * again before the date rolls over.
     */
    private suspend fun currentStreakCached(date: String): Int {
        val (cachedDays, cachedDate) = preferencesRepository.cachedStreak.first()
        if (cachedDate == date) return cachedDays
        val streak = habitRepository.computeCurrentStreak()
        preferencesRepository.setCachedStreak(streak, date)
        return streak
    }

    companion object {
        /** Pure arithmetic pulled out of [effectiveWindowMinutes] so it's testable without a real [HabitRepository]/[PreferencesRepository]. */
        fun computeEffectiveWindowMinutes(baseMinutes: Int, bonusMinutes: Int): Int =
            (baseMinutes + bonusMinutes.coerceAtLeast(0))
                .coerceIn(baseMinutes, PreferencesRepository.MAX_LIMITED_UNBLOCK_WINDOW_MINUTES)
    }
}
