package com.habitsfirst.androidclone.data.remote

import com.habitsfirst.androidclone.domain.model.AccountabilityBuddy
import com.habitsfirst.androidclone.domain.model.DailySummary
import com.habitsfirst.androidclone.domain.model.PairingCode

/**
 * The seam a future real accountability-buddy backend sits behind. Nothing in the app
 * should call this directly -- go through
 * [com.habitsfirst.androidclone.data.repository.AccountabilityRepository] instead, so
 * that swapping [HttpAccountabilityApiClient] (bound in `di/AccountabilityModule.kt`)
 * for a different implementation, or pointing it at a real hosted backend, requires zero
 * changes at the call sites. Mirrors the shape of
 * [com.habitsfirst.androidclone.data.billing.EntitlementRepository] /
 * [com.habitsfirst.androidclone.data.verification.ImageVerificationClient].
 *
 * There is no default backend today -- every method here is expected to fail (typically
 * [AccountabilityApiException.NoBackendConfigured] until a base URL is set in Settings,
 * or [AccountabilityApiException.Network] once one is set but nothing is actually
 * listening there) until a real server exists. Callers must treat that as the normal
 * case, not an exceptional one.
 */
interface AccountabilityApiClient {
    /** Asks the backend to mint a fresh pairing code for this device to share with a buddy. */
    suspend fun createPairingCode(): PairingCode

    /** Redeems a buddy's pairing code with the backend, returning the newly paired buddy. */
    suspend fun addBuddy(code: String): AccountabilityBuddy

    /** Uploads this device's current daily summary for buddies to see. */
    suspend fun pushDailySummary(summary: DailySummary): Result<Unit>

    /** Every paired buddy's latest known daily summary, as last reported by the backend. */
    suspend fun fetchBuddySummaries(): List<AccountabilityBuddy>
}

/** Typed failures for [AccountabilityApiClient] -- callers should show [message], never crash on one of these. */
sealed class AccountabilityApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No backend base URL is configured yet -- point the user at Settings. */
    object NoBackendConfigured : AccountabilityApiException("No accountability backend is configured. Add one in Settings.")

    /** The backend was unreachable (host down, no DNS, timed out, ...) -- the common case today, since none is hosted by default. */
    class Network(message: String, cause: Throwable? = null) : AccountabilityApiException(message, cause)

    /** The backend responded, but with a non-2xx status or a body this client couldn't understand. */
    class Api(message: String) : AccountabilityApiException(message)
}
