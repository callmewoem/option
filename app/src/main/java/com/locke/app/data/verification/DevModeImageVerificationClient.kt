package com.locke.app.data.verification

import com.locke.app.BuildConfig
import com.locke.app.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes to [MockImageVerificationClient] instead of the real
 * [BackendImageVerificationClient] while Settings -> Developer's "Developer mode" toggle
 * is on, so photo verification works on a debug build with no backend running (and no
 * Anthropic usage against it). Bound in place of [BackendImageVerificationClient] in
 * `di/VerificationModule.kt` -- every other call site keeps going through the
 * [ImageVerificationClient] seam and needs no changes.
 *
 * `BuildConfig.DEBUG` is the actual gate here, not just the Settings row's visibility --
 * a release build always uses [BackendImageVerificationClient], even if the stored
 * preference were somehow left on.
 */
@Singleton
class DevModeImageVerificationClient @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val real: BackendImageVerificationClient,
    private val mock: MockImageVerificationClient,
) : ImageVerificationClient {

    override suspend fun verify(request: VerificationRequest): VerificationResult {
        val devModeActive = BuildConfig.DEBUG && preferencesRepository.isDeveloperModeEnabled.first()
        return if (devModeActive) mock.verify(request) else real.verify(request)
    }
}
