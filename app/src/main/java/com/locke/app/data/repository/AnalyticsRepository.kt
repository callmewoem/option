package com.locke.app.data.repository

import com.locke.app.data.local.dao.AnalyticsEventDao
import com.locke.app.data.local.entity.AnalyticsEventEntity
import com.locke.app.data.remote.AnalyticsApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single seam call sites go through to log a product-analytics event -- see
 * `util/AnalyticsEvents.kt` for the fixed event names, and `PRIVACY_POLICY.md`'s
 * "Analytics" section for what this promises never to include. Queues locally
 * (Room, [AnalyticsEventDao]) rather than calling the network directly -- [logEvent]
 * never blocks on or fails because of connectivity -- and is flushed in batches by
 * [com.locke.app.service.AnalyticsUploadWorker], the same outbox shape as
 * [AccountabilityRepository]'s daily-summary sync.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val dao: AnalyticsEventDao,
    private val apiClient: AnalyticsApiClient,
    private val preferencesRepository: PreferencesRepository,
) {
    /** Settings -> Privacy's "Share anonymous usage analytics" toggle. On by default. */
    val isEnabled: Flow<Boolean> = preferencesRepository.isAnalyticsEnabled

    /**
     * Queues [name] (one of [com.locke.app.util.AnalyticsEvents]' constants) with
     * [properties] for the next upload batch -- a no-op, not even queued, if analytics
     * is turned off. Fire-and-forget from the caller's perspective: launch this in
     * `viewModelScope` and never await or branch on the result.
     */
    suspend fun logEvent(name: String, properties: Map<String, Any?> = emptyMap()) {
        if (!preferencesRepository.isAnalyticsEnabled.first()) return
        val propertiesJson = JSONObject(properties.filterValues { it != null }).toString()
        dao.insert(AnalyticsEventEntity(name = name, propertiesJson = propertiesJson))
    }

    /**
     * Turning analytics off here only stops *future* [logEvent] calls from queuing
     * anything -- it doesn't retroactively delete events already queued or already
     * uploaded (see `PRIVACY_POLICY.md`). Whatever's still queued at the moment this
     * is called still gets uploaded by the next [flushBatch].
     */
    suspend fun setEnabled(enabled: Boolean) = preferencesRepository.setAnalyticsEnabled(enabled)

    /**
     * Uploads and clears up to [BATCH_SIZE] of the oldest queued events. Called
     * repeatedly by [com.locke.app.service.AnalyticsUploadWorker] until the queue is
     * drained or a batch fails. Returns whether the queue is now (as far as this call
     * knows) fully drained -- false means either there's more queued after this batch,
     * or this batch's upload failed and everything in it is still queued for next time.
     */
    suspend fun flushBatch(): Boolean {
        val batch = dao.getBatch(BATCH_SIZE)
        if (batch.isEmpty()) return true
        val uploaded = apiClient.uploadEvents(batch)
        if (uploaded) dao.deleteByIds(batch.map { it.id })
        return uploaded && batch.size < BATCH_SIZE
    }

    companion object {
        private const val BATCH_SIZE = 100
    }
}
