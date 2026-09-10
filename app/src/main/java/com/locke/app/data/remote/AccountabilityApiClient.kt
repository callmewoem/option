package com.locke.app.data.remote

import com.locke.app.domain.model.AccountabilityBuddy
import com.locke.app.domain.model.DailySummary
import com.locke.app.domain.model.PairingCode

/**
 * The seam the accountability-buddy backend (`backend/`, see `routes/buddies.js`) sits
 * behind. Nothing in the app should call this directly -- go through
 * [com.locke.app.data.repository.AccountabilityRepository] instead, so
 * that swapping [HttpAccountabilityApiClient] (bound in `di/AccountabilityModule.kt`)
 * for a different implementation requires zero changes at the call sites. Mirrors the
 * shape of [com.locke.app.data.billing.EntitlementRepository] /
 * [com.locke.app.data.verification.ImageVerificationClient].
 *
 * Free tier gets one real buddy connection
 * ([com.locke.app.data.repository.PreferencesRepository.MAX_FREE_BUDDIES]) --
 * [addBuddy] is the one call the backend actually gates server-side on that (HTTP
 * 402, checked on both sides of the pairing since either device could be the one at
 * its cap); callers should check
 * [com.locke.app.data.repository.AccountabilityRepository.canAddBuddy] first and
 * route to the paywall rather than let that 402 be the primary UX. [createPairingCode]
 * and [pushDailySummary] are never gated -- minting an invite is free, and sharing
 * with a buddy you already have costs nothing extra once the connection exists.
 */
interface AccountabilityApiClient {
    /** Asks the backend to mint a fresh pairing code for this device to share with a buddy. */
    suspend fun createPairingCode(): PairingCode

    /** Redeems a buddy's pairing code with the backend, returning the newly paired buddy. Free tier: 402 if either side is already at its buddy cap. */
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
