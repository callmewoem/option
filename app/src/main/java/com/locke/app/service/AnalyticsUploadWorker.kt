package com.locke.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.locke.app.data.repository.AnalyticsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically drains [AnalyticsRepository]'s local event queue to the backend, a few
 * batches at a time. A failed or offline run just leaves whatever's still queued for
 * the next tick, same "try now, leave it queued on failure" shape as
 * [com.locke.app.data.repository.AccountabilityRepository]'s daily-summary sync --
 * see [AnalyticsRepository.flushBatch].
 */
@HiltWorker
class AnalyticsUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val analyticsRepository: AnalyticsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Bounded, not "until empty" -- a queue that somehow grew huge (analytics was
        // off for a long stretch, then turned back on) drains over a few periodic
        // ticks instead of one worker run trying to upload everything at once.
        repeat(MAX_BATCHES_PER_RUN) {
            val drained = analyticsRepository.flushBatch()
            if (drained) return Result.success()
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "analytics_upload_periodic"
        private const val MAX_BATCHES_PER_RUN = 5
    }
}
