package com.habitsfirst.androidclone.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.data.healthconnect.HealthConnectManager
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.domain.model.HabitType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically refreshes progress for STEPS, WORKOUT_MINUTES, and SLEEP_HOURS habits from
 * Health Connect, mirroring [UsageTrackingWorker]'s approach for "use an app" habits: read an
 * absolute today-so-far (or, for sleep, trailing-24h) value and overwrite the stored progress
 * with it. Only scheduled while sync is enabled in Settings (see `WorkScheduler`), and reads
 * as a no-op if the user hasn't granted the read permission -- [HealthConnectManager] returns
 * 0 in that case rather than throwing, so this just leaves progress unchanged either way
 * (setProgress with the same value is harmless).
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val healthConnectManager: HealthConnectManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!healthConnectManager.isAvailable || !healthConnectManager.hasPermissions()) {
            return Result.success()
        }

        val habits = habitRepository.getHealthConnectHabitsOnce()
        if (habits.isEmpty()) return Result.success()

        val steps = healthConnectManager.todaySteps()
        val workoutMinutes = healthConnectManager.workoutMinutesToday()
        val sleepHours = healthConnectManager.recentSleepHours()
        for (habit in habits) {
            val current = when (habit.type) {
                HabitType.STEPS -> steps
                HabitType.WORKOUT_MINUTES -> workoutMinutes
                HabitType.SLEEP_HOURS -> sleepHours
                else -> continue
            }
            habitRepository.setProgress(habit.id, current, habit.targetValue)
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "health_connect_sync_periodic"
        const val ONE_OFF_NAME = "health_connect_sync_one_off"
    }
}
