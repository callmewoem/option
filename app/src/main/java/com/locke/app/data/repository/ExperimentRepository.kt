package com.locke.app.data.repository

import com.locke.app.data.remote.ExperimentApiClient
import com.locke.app.data.remote.ExperimentApiException
import com.locke.app.domain.model.ExperimentKeys
import com.locke.app.domain.model.PaywallPlanEmphasis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single seam UI/domain code goes through to read this device's A/B experiment
 * assignments (`backend/src/services/experiments.js` defines what's actually running)
 * -- nothing should call [ExperimentApiClient] directly. Assignments are fetched once
 * per app launch ([refreshIfNeeded], called from
 * `ui/navigation/SplashViewModel.kt`) and cached in
 * [PreferencesRepository.cachedExperimentAssignments], so every typed getter below is
 * an instant, offline-safe Flow read -- no call site blocks on a network round trip
 * to know which variant it's in. A device that's never successfully reached the
 * backend (offline since install, or the backend genuinely down) simply reads as
 * every experiment's control/default value below -- the same "fail toward the least
 * surprising behavior" choice the app already makes elsewhere (e.g. an unset backend
 * URL disabling accountability buddies rather than crashing), rather than a locally
 * randomized guess that would disagree with whatever the backend eventually assigns.
 */
@Singleton
class ExperimentRepository @Inject constructor(
    private val apiClient: ExperimentApiClient,
    private val preferencesRepository: PreferencesRepository,
) {

    /**
     * Re-fetches this device's assignments if the cache is missing or older than
     * [MIN_REFRESH_INTERVAL_MILLIS]. Safe to call on every app launch (see
     * `SplashViewModel`) -- a fresh-enough cache makes this a cheap no-op. Any backend
     * failure (offline, unreachable, non-2xx) just leaves the existing cache (possibly
     * still empty, on a first launch that's never been online) untouched; every typed
     * getter below has its own default for that case.
     */
    suspend fun refreshIfNeeded(forceRefresh: Boolean = false) {
        val lastFetched = preferencesRepository.experimentAssignmentsFetchedAtEpochMillis.first()
        val now = System.currentTimeMillis()
        if (!forceRefresh && lastFetched != 0L && now - lastFetched < MIN_REFRESH_INTERVAL_MILLIS) return
        try {
            val assignments = apiClient.fetchAssignments()
            preferencesRepository.setCachedExperimentAssignments(assignments.associate { it.key to it.value })
        } catch (e: ExperimentApiException) {
            // Keep whatever's cached -- see class doc.
        }
    }

    /** Raw cached value for [key], or null if it's never been assigned (offline since install, or an unrecognized key). */
    fun rawValue(key: String): Flow<String?> = preferencesRepository.cachedExperimentAssignments.map { it[key] }

    /**
     * [ExperimentKeys.GATING_HABIT_CAP] -- free-tier cap on new GATING habits,
     * overriding [PreferencesRepository.MAX_FREE_GATING_HABITS] once assigned. See
     * `ui/habit/AddEditHabitViewModel.kt`'s free-tier check.
     */
    val gatingHabitCap: Flow<Int> = rawValue(ExperimentKeys.GATING_HABIT_CAP).map { raw ->
        raw?.toIntOrNull()?.takeIf { it > 0 } ?: PreferencesRepository.MAX_FREE_GATING_HABITS
    }

    /** [ExperimentKeys.PAYWALL_PLAN_EMPHASIS] -- which plan the paywall sorts first and visually highlights. */
    val paywallPlanEmphasis: Flow<PaywallPlanEmphasis> = rawValue(ExperimentKeys.PAYWALL_PLAN_EMPHASIS).map { raw ->
        PaywallPlanEmphasis.fromValue(raw)
    }

    /**
     * [ExperimentKeys.ONBOARDING_PAYWALL_STEP] -- whether onboarding ends with its own
     * skippable Premium pitch step at all. The pitch stays reachable from Settings
     * either way -- this only affects whether onboarding itself detours through it.
     */
    val isOnboardingPaywallStepShown: Flow<Boolean> = rawValue(ExperimentKeys.ONBOARDING_PAYWALL_STEP).map { raw ->
        raw != "hidden"
    }

    companion object {
        /** Assignments rarely change once made; this just bounds retrying the fetch on every single launch while offline. */
        private const val MIN_REFRESH_INTERVAL_MILLIS = 60 * 60 * 1000L // 1 hour
    }
}
