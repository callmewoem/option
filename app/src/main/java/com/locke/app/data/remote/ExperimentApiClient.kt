package com.locke.app.data.remote

import com.locke.app.domain.model.ExperimentAssignment

/**
 * The seam the A/B-experiment backend (`backend/`, see `routes/experiments.js`) sits
 * behind. Nothing in the app should call this directly -- go through
 * [com.locke.app.data.repository.ExperimentRepository] instead, same shape as
 * [AccountabilityApiClient]/[com.locke.app.data.billing.EntitlementRepository].
 */
interface ExperimentApiClient {
    /** This device's assignment for every experiment the backend currently runs. */
    suspend fun fetchAssignments(): List<ExperimentAssignment>
}

/** Typed failures for [ExperimentApiClient] -- callers should fall back silently to a cached/local default, never crash on one of these. */
sealed class ExperimentApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The backend was unreachable (host down, no DNS, timed out, offline, ...), including while registering this device's identity. */
    class Network(message: String, cause: Throwable? = null) : ExperimentApiException(message, cause)

    /** The backend responded, but with a non-2xx status or a body this client couldn't understand. */
    class Api(message: String) : ExperimentApiException(message)
}
