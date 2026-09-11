package com.locke.app.data.remote

import com.locke.app.data.local.entity.AnalyticsEventEntity

/**
 * The seam the analytics backend (`backend/`, see `routes/analytics.js`) sits behind.
 * Nothing in the app should call this directly -- go through
 * [com.locke.app.data.repository.AnalyticsRepository] instead, same shape as
 * [AccountabilityApiClient]/[ExperimentApiClient].
 */
interface AnalyticsApiClient {
    /**
     * Uploads [events] as one batch. Returns whether the batch was accepted -- a
     * plain `Boolean`, not a typed exception, since the only thing a caller
     * ([com.locke.app.data.repository.AnalyticsRepository.flush]) does with a
     * failure is leave the events queued for next time, the same "try now, leave it
     * queued on failure" shape as
     * [com.locke.app.data.repository.AccountabilityRepository].
     */
    suspend fun uploadEvents(events: List<AnalyticsEventEntity>): Boolean
}
