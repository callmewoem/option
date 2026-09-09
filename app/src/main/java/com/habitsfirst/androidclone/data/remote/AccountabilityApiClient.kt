package com.habitsfirst.androidclone.data.remote

import com.habitsfirst.androidclone.domain.model.AccountabilityBuddy
import com.habitsfirst.androidclone.domain.model.DailySummary
import com.habitsfirst.androidclone.domain.model.PairingCode

/**
 * The seam the accountability-buddy backend (`backend/`, see `routes/buddies.js`) sits
 * behind. Nothing in the app should call this directly -- go through
 * [com.habitsfirst.androidclone.data.repository.AccountabilityRepository] instead, so
 * that swapping [HttpAccountabilityApiClient] (bound in `di/AccountabilityModule.kt`)
 * for a different implementation requires zero changes at the call sites. Mirrors the
 * shape of [com.habitsfirst.androidclone.data.billing.EntitlementRepository] /
 * [com.habitsfirst.androidclone.data.verification.ImageVerificationClient].
 *
 * Buddies are a premium feature (the backend enforces this server-side, HTTP 402, on
 * every call below except [fetchBuddySummaries]) -- callers should check
 * [com.habitsfirst.androidclone.data.billing.EntitlementRepository.isPremium] first and
 * route to the paywall rather than let a call fail here as the primary UX.
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
    /** The backend was unreachable (host down, no DNS, timed out, offline, ...), including while registering this device's identity. */
    class Network(message: String, cause: Throwable? = null) : AccountabilityApiException(message, cause)

    /** The backend responded, but with a non-2xx status (e.g. 402 "premium required") or a body this client couldn't understand. */
    class Api(message: String) : AccountabilityApiException(message)
}
