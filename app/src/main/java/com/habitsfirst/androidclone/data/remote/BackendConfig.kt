package com.habitsfirst.androidclone.data.remote

import com.habitsfirst.androidclone.BuildConfig

/**
 * Base URL of Locke's own backend (see the root `backend/` directory) -- baked in at
 * build time via `BACKEND_BASE_URL` (`app/build.gradle.kts`), not user-configurable.
 * Photo verification ([com.habitsfirst.androidclone.data.verification.BackendImageVerificationClient]),
 * the accountability-buddy API ([HttpAccountabilityApiClient]), and subscription
 * verification ([com.habitsfirst.androidclone.data.billing.PlayBillingEntitlementRepository])
 * all go through here.
 */
object BackendConfig {
    val BASE_URL: String = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
}
