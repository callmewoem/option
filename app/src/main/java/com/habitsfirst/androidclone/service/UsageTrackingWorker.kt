package com.habitsfirst.androidclone.service

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val usageStatsManager =
            applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return Result.failure()

        val appUsageHabits = habitRepository.getAppUsageHabitsOnce()
        if (appUsageHabits.isEmpty()) return Result.success()

        val startOfDay = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)

        for (habit in appUsageHabits) {
            val packageName = habit.targetPackageName ?: continue
            val foregroundMillis = stats[packageName]?.totalTimeInForeground ?: 0L
            val minutes = (foregroundMillis / 60_000L).toInt()
            habitRepository.setProgress(habit.id, minutes, habit.targetValue)
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "usage_tracking_periodic"
        const val ONE_OFF_NAME = "usage_tracking_one_off"
    }
}
