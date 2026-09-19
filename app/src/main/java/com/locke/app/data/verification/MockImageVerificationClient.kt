package com.locke.app.data.verification

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-approves every photo with no network call at all -- what
 * [DevModeImageVerificationClient] routes to while Settings -> Developer's toggle is on,
 * so a photo-verification or morning-check-in habit can be exercised on a debug build
 * with no backend running and no Anthropic usage. Never bound directly; only reachable
 * through [DevModeImageVerificationClient]'s own `BuildConfig.DEBUG` check.
 */
@Singleton
class MockImageVerificationClient @Inject constructor() : ImageVerificationClient {

    override suspend fun verify(request: VerificationRequest): VerificationResult {
        // A short, fixed delay so the caller's loading state gets exercised the same as
        // a real round trip would, instead of resolving suspiciously instantly.
        delay(MOCK_DELAY_MILLIS)
        return VerificationResult(
            approved = true,
            reasoning = "Developer mode: auto-approved locally -- no backend call was made.",
        )
    }

    private companion object {
        const val MOCK_DELAY_MILLIS = 400L
    }
}
