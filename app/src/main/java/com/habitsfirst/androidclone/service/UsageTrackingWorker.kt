package com.habitsfirst.androidclone.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.data.repository.EaseInRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically refreshes progress for "use this app for N minutes" habits by reading
 * today's per-app foreground time from `UsageStatsManager` (see [AppUsageSyncer]). Also
 * runs as a one-off job right after the user leaves a tracked app, so progress doesn't
 * lag a full 15 minutes -- and again from Settings -> Diagnostics, for manual testing.
 */
@HiltWorker
class UsageTrackingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val easeInRepository: EaseInRepository,
    private val appUsageSyncer: AppUsageSyncer,
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The actual usage-stats sync runs first and unconditionally: it's this
        // worker's entire reason for existing (and for a one-off refresh, the only
        // thing the caller is waiting on), so it must not be skippable by a failure
        // in the unrelated housekeeping below. It's isolated in its own runCatching
        // for the same reason -- a single bad day (e.g. a stale makeup habit, or a
        // ramp in a state its own edge case didn't expect) must never be able to
        // silently abort a habit's progress write ever again.
        val syncResult = runCatching { appUsageSyncer.sync() }
        syncResult.exceptionOrNull()?.let { e ->
            // AppUsageSyncer.sync() records its own outcome on every path it returns
            // from -- this only fires if it didn't even get that far (crashed before
            // its own try/catch), so the failure would otherwise go completely unrecorded.
            Log.w(TAG, "App-usage sync crashed", e)
            preferencesRepository.recordUsageSyncOutcome(habitCount = 0, error = "Sync crashed: ${e.message ?: e::class.simpleName}")
        }

        // Piggybacks on this worker's existing 15-min cadence to clean up any makeup
        // habit (see PenaltyRepository) whose one-day expiry has passed, and to check
        // whether the ease-in ramp's next habit is ready to graduate to GATING.
        // Isolated in its own try/catch for the same reason as above -- it's unrelated
        // to app-usage tracking and must never be able to take that down with it.
        runCatching {
            habitRepository.archiveExpiredHabits()
            easeInRepository.maybeGraduateNextHabit()
        }.onFailure { Log.w(TAG, "Habit housekeeping failed", it) }

        return if (syncResult.isSuccess) Result.success() else Result.failure()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "usage_tracking_periodic"
        const val ONE_OFF_NAME = "usage_tracking_one_off"
        private const val TAG = "UsageTrackingWorker"
    }
}
