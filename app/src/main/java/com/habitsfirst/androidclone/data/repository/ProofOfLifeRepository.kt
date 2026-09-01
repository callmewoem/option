package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A daily "prove you're actually up" check-in, gated the same way as a photo-verification
 * habit -- but it isn't one, since it's not tied to any single [com.habitsfirst.androidclone.domain.model.Habit]
 * and its consequence is a [PenaltyRepository] penalty rather than a habit completion. The
 * UI ([com.habitsfirst.androidclone.ui.proofoflife.ProofOfLifeViewModel]) is a thin wrapper
 * around the same [com.habitsfirst.androidclone.data.verification.ImageVerificationClient]
 * every photo-verification habit uses; this repository just tracks whether today's check-in
 * happened and penalizes once if the window passes without one.
 */
@Singleton
class ProofOfLifeRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val penaltyRepository: PenaltyRepository,
) {
    val settings: Flow<PreferencesRepository.ProofOfLifeSettings> = preferencesRepository.proofOfLifeSettings

    suspend fun setProofOfLife(enabled: Boolean, time: String, windowMinutes: Int) {
        preferencesRepository.setProofOfLifeSettings(enabled, time, windowMinutes)
    }

    suspend fun isConfirmedToday(): Boolean =
        preferencesRepository.proofOfLifeConfirmedDate.first() == DateProvider.todayString()

    /** Re-derives "today" as the date actually changes -- see [DateProvider.currentDateFlow]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isConfirmedTodayFlow: Flow<Boolean> = DateProvider.currentDateFlow().flatMapLatest { today ->
        preferencesRepository.proofOfLifeConfirmedDate.map { it == today }
    }

    suspend fun confirmToday() {
        preferencesRepository.setProofOfLifeConfirmedDate(DateProvider.todayString())
    }

    /**
     * Called on the same ~15-minute cadence as the app's other periodic workers. Applies
     * [PenaltyRepository]'s block-extension penalty once per day once [windowMinutes] have
     * passed since the configured time without a confirmed check-in. Unlike the morning todo
     * reminder, this deliberately has no upper cutoff -- a late-caught miss (say the device
     * was asleep for hours) should still be felt, not silently skipped.
     */
    suspend fun checkAndPenalizeIfMissed() {
        val settings = settings.first()
        if (!settings.enabled) return

        val today = DateProvider.todayString()
        if (preferencesRepository.proofOfLifeConfirmedDate.first() == today) return
        if (preferencesRepository.proofOfLifeLastPenalizedDate.first() == today) return

        val target = runCatching { LocalTime.parse(settings.time) }.getOrDefault(LocalTime.of(8, 0))
        val minutesPast = Duration.between(target, LocalTime.now()).toMinutes()
        if (minutesPast < settings.windowMinutes) return

        preferencesRepository.setProofOfLifeLastPenalizedDate(today)
        penaltyRepository.extendBlock(PENALTY_MINUTES, reason = "missed morning check-in")
    }

    companion object {
        const val PENALTY_MINUTES = 30
    }
}
