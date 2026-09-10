package com.locke.app.data.remote

/**
 * Low-level failures talking to Locke's own backend (`backend/`) -- thrown by
 * [DeviceIdentityRepository] (device registration) and used as the common shape every
 * backend-facing client normalizes IO/HTTP failures into. Higher-level clients
 * ([HttpAccountabilityApiClient], [com.locke.app.data.verification.BackendImageVerificationClient])
 * catch this and re-throw it as their own domain exception type, so callers never need
 * to know this type exists.
 */
sealed class BackendApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The backend was unreachable (host down, no DNS, timed out, offline, ...). */
    class Network(message: String, cause: Throwable? = null) : BackendApiException(message, cause)

    /** The backend responded, but with a non-2xx status or a body this client couldn't understand. */
    class Api(message: String) : BackendApiException(message)
}
