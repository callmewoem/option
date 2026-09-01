package com.habitsfirst.androidclone.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.data.repository.ProofOfLifeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs on the same ~15-minute cadence as [UsageTrackingWorker]/[MorningReminderWorker] and
 * hands off to [ProofOfLifeRepository], which no-ops unless the check-in is enabled, already
 * confirmed today, or already penalized today.
 */
@HiltWorker
class ProofOfLifeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val proofOfLifeRepository: ProofOfLifeRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        proofOfLifeRepository.checkAndPenalizeIfMissed()
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "proof_of_life_check_periodic"
    }
}
