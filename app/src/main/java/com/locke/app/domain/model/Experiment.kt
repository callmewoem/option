package com.locke.app.domain.model

/**
 * One device's assignment for one A/B experiment, as returned by `GET /v1/experiments`
 * (`backend/src/services/experiments.js` defines the actual experiments -- this is
 * deliberately just a generic `(key, variant, value)` triple rather than one Kotlin
 * type per experiment, so a new experiment needs no app release to add: the backend
 * ships it, [com.locke.app.data.repository.ExperimentRepository] fetches and caches
 * it same as any other, and only the one call site that cares about the new key needs
 * to read it.
 */
data class ExperimentAssignment(
    val key: String,
    val variant: String,
    /** The variant's actual config payload (a number, a string id, ...) -- what a call site should act on, not [variant] itself. */
    val value: String,
)

/**
 * The experiment keys Locke currently has call sites for. Matches
 * `backend/src/services/experiments.js.EXPERIMENTS`'s `key`s exactly -- see that
 * file's own doc for adding a new one.
 */
object ExperimentKeys {
    /** [value] is the free-tier cap on new GATING habits, overriding [com.locke.app.data.repository.PreferencesRepository.MAX_FREE_GATING_HABITS]. */
    const val GATING_HABIT_CAP = "gating_habit_cap"

    /** [value] is a [PaywallPlanEmphasis] name -- which plan the paywall sorts first and highlights. */
    const val PAYWALL_PLAN_EMPHASIS = "paywall_plan_emphasis"

    /** [value] is "shown" or "hidden" -- whether onboarding's own Premium pitch step is included at all. */
    const val ONBOARDING_PAYWALL_STEP = "onboarding_paywall_step"
}

/** [ExperimentKeys.PAYWALL_PLAN_EMPHASIS]'s two variants. */
enum class PaywallPlanEmphasis {
    ANNUAL,
    MONTHLY,
    ;

    companion object {
        fun fromValue(value: String?): PaywallPlanEmphasis = when (value) {
            "monthly" -> MONTHLY
            else -> ANNUAL // unknown/missing/"annual" -- matches the backend's "control" variant
        }
    }
}
