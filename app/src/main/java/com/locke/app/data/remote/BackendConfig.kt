package com.locke.app.data.remote

import com.locke.app.BuildConfig

/**
 * Base URL of Locke's own backend (see the root `backend/` directory) -- baked in at
 * build time via `BACKEND_BASE_URL` (`app/build.gradle.kts`), not user-configurable.
 * Photo verification ([com.locke.app.data.verification.BackendImageVerificationClient]),
 * the accountability-buddy API ([HttpAccountabilityApiClient]), and subscription
 * verification ([com.locke.app.data.billing.PlayBillingEntitlementRepository])
 * all go through here.
 */
object BackendConfig {
    val BASE_URL: String = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
}
