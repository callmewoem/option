package com.locke.app.util

/**
 * Fixed, lowercase snake_case event names logged via
 * [com.locke.app.data.repository.AnalyticsRepository.logEvent] -- never a
 * free-typed string at the call site, so the backend's `NAME_PATTERN` validation
 * (`backend/src/routes/analytics.js`) and any future analysis both have a closed,
 * known set to work with. Property keys are similarly kept short and generic
 * (`feature`, `variant`, `productId`, ...) -- never a habit's name/text, a photo, or
 * anything else the user typed, matching the "no PII by construction" promise in
 * `PRIVACY_POLICY.md`.
 */
object AnalyticsEvents {
    const val PAYWALL_SHOWN = "paywall_shown"
    const val PAYWALL_CTA_TAPPED = "paywall_cta_tapped"
    const val PURCHASE_COMPLETED = "purchase_completed"
    const val GATING_LIMIT_REACHED = "gating_limit_reached"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val ONBOARDING_PAYWALL_STEP_SKIPPED = "onboarding_paywall_step_skipped"
    const val ANALYTICS_OPT_OUT_CHANGED = "analytics_opt_out_changed"
}
