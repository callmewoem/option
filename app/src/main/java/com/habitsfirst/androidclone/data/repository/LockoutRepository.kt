package com.habitsfirst.androidclone.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A self-imposed, on-demand lockout: while active, every app on the block list stays
 * locked until [lockoutUntilEpochMillis] passes -- not gated by today's habits, not
 * bypassable by a grace token (see
 * [com.habitsfirst.androidclone.service.AppBlockAccessibilityService.evaluateLockState]),
 * same hard-commitment treatment as [BedtimeRepository]'s curfew. Unlike bedtime, this
 * isn't a recurring daily window -- it's a one-shot span the user starts from Home for
 * however long they pick (see [DURATION_PRESETS_MINUTES]), e.g. "block everything for
 * the next 2 hours."
 *
 * It can be cancelled early, but only from Home itself -- Locke's own package is never
 * on the block list, so Home stays reachable through a lockout, same as it does through
 * bedtime. The block screen a locked app shows offers no way through, same as curfew
 * (see [com.habitsfirst.androidclone.ui.block.LockoutScreen]): talking yourself out of
 * a lockout mid-craving, from inside the very app it's meant to keep you out of, isn't
 * something this gives you. Deliberately going back to Locke to end it early is a
 * different, more honest act, and is left alone (with a confirmation, in
 * [com.habitsfirst.androidclone.ui.home.LockoutDialog]) rather than blocked outright.
 */
@Singleton
class LockoutRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) {
    val lockoutUntilEpochMillis: Flow<Long> = preferencesRepository.lockoutUntilEpochMillis

    suspend fun isLockoutActiveNow(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        preferencesRepository.lockoutUntilEpochMillis.first() > nowEpochMillis

    suspend fun startLockout(minutes: Int) {
        preferencesRepository.setLockoutUntil(System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L)
    }

    suspend fun cancelLockout() {
        preferencesRepository.setLockoutUntil(0L)
    }

    companion object {
        /** Duration presets offered on Home's lockout sheet, in minutes. */
        val DURATION_PRESETS_MINUTES = listOf(15, 30, 60, 120, 240)
    }
}
