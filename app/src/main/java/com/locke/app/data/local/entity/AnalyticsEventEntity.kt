package com.locke.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One queued analytics event, waiting to be batched up and POSTed to
 * `/v1/analytics/events` by [com.locke.app.service.AnalyticsUploadWorker]. An
 * outbox, same shape as [PendingStatsSyncEntity]'s "queue locally, flush
 * opportunistically" pattern -- logging an event ([com.locke.app.data.repository.
 * AnalyticsRepository.logEvent]) never blocks on the network or fails silently if
 * offline. [propertiesJson] is a small, non-identifying JSON object the caller
 * builds -- see [com.locke.app.util.AnalyticsEvents]'s own doc on what never
 * belongs in it.
 */
@Entity(tableName = "analytics_events")
data class AnalyticsEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val propertiesJson: String = "{}",
    val clientTimestampEpochMillis: Long = System.currentTimeMillis(),
)
