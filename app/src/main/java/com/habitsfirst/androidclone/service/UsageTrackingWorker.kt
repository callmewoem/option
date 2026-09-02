package com.habitsfirst.androidclone.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.data.repository.EaseInRepository
import com.habitsfirst.androidclone.data.repository.HabitRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Periodically refreshes progress for "use this app for N minutes" habits by reading
 * today's per-app foreground time from [UsageStatsManager]. Also runs as a one-off job
 * right after the user leaves a tracked app, so progress doesn't lag a full 15 minutes.
 */
@HiltWorker
class UsageTrackingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val easeInRepository: EaseInRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The actual usage-stats sync runs first and unconditionally: it's this
        // worker's entire reason for existing (and for a one-off refresh, the only
        // thing the caller is waiting on), so it must not be skippable by a failure
        // in the unrelated housekeeping below. Previously that housekeeping ran
        // first with nothing catching its exceptions, so a single bad day (e.g. a
        // stale makeup habit or a ramp in a state its own edge case didn't expect)
        // would silently abort the whole run before a single habit's progress was
        // ever written -- every future run hits the same exception, so a habit's
        // progress could get stuck at 0 forever with no error surfaced anywhere.
        val syncResult = runCatching { syncAppUsageProgress() }
        syncResult.exceptionOrNull()?.let { Log.w(TAG, "App-usage sync failed", it) }

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

    private suspend fun syncAppUsageProgress() {
        val usageStatsManager =
            applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return

        val appUsageHabits = habitRepository.getAppUsageHabitsOnce()
        if (appUsageHabits.isEmpty()) return

        val startOfDay = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)

        for (habit in appUsageHabits) {
            val packageName = habit.targetPackageName ?: continue
            // One habit's write failing (or a null targetValue mismatch, etc.)
            // shouldn't stop the rest of the batch from being recorded.
            runCatching {
                val foregroundMillis = stats[packageName]?.totalTimeInForeground ?: 0L
                val minutes = (foregroundMillis / 60_000L).toInt()
                habitRepository.setProgress(habit.id, minutes, habit.targetValue)
            }.onFailure { Log.w(TAG, "Failed to update progress for habit ${habit.id}", it) }
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "usage_tracking_periodic"
        const val ONE_OFF_NAME = "usage_tracking_one_off"
        private const val TAG = "UsageTrackingWorker"
    }
}
