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
 * that unlock only lasts [WINDOW_MINUTES] before blocked apps re-lock, even though the
 * habits are (and stay) complete for the rest of the day. Never bypasses bedtime or a
 * permanent URL block, same as habit gating itself; a grace token still bypasses it,
 * same as it bypasses habit gating.
 *
 * The window's start is stamped lazily -- the first time [isWithinUnlockWindow] sees a
 * date it hasn't stamped yet -- rather than at the exact instant a habit gets marked
 * done. Habit completion can happen from more than one place (Home's own actions, but
 * also the app-usage and Health Connect background workers), so there's no single call
 * site to hook reliably; this mirrors how [LootboxRepository.maybeAwardDailyLootbox]
 * already guards its own once-per-day state. In practice the window starts at whichever
 * comes first: the moment the user next checks a blocked app or site after finishing,
 * or right when the last habit is completed from Home.
 */
@Singleton
class LimitedUnblockRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
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
        return nowEpochMillis < startedAt + WINDOW_MINUTES * 60_000L
    }

    /** Returns today's window start, stamping it as [nowEpochMillis] if this is the first call for [date]. */
    private suspend fun stampWindowStartIfNeeded(date: String, nowEpochMillis: Long): Long {
        if (preferencesRepository.habitsCompleteUnlockWindowDate.first() == date) {
            return preferencesRepository.habitsCompleteUnlockWindowStartedAtEpochMillis.first()
        }
        preferencesRepository.stampHabitsCompleteUnlockWindowStart(date, nowEpochMillis)
        return nowEpochMillis
    }

    companion object {
        const val WINDOW_MINUTES = 60
    }
}
