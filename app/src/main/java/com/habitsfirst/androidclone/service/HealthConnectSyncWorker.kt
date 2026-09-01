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
 * Periodically refreshes progress for STEPS and EXERCISE_MINUTES habits from Health
 * Connect, mirroring [UsageTrackingWorker]'s approach for "use an app" habits: read an
 * absolute today-so-far value and overwrite the stored progress with it. Only scheduled
 * while sync is enabled in Settings (see `WorkScheduler`), and reads as a no-op if the
 * user hasn't granted the two read permissions -- [HealthConnectManager] returns 0 in
 * that case rather than throwing, so this just leaves progress unchanged either way
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

        // lazy{} can't call a suspend function, so these are only actually fetched when
        // a habit of that type exists to consume them.
        val steps = if (habits.any { it.type == HabitType.STEPS }) healthConnectManager.todaySteps() else 0
        val exerciseMinutes =
            if (habits.any { it.type == HabitType.EXERCISE_MINUTES }) healthConnectManager.todayExerciseMinutes() else 0

        for (habit in habits) {
            val value = when (habit.type) {
                HabitType.STEPS -> steps
                HabitType.EXERCISE_MINUTES -> exerciseMinutes
                else -> continue
            }
            habitRepository.setProgress(habit.id, value, habit.targetValue)
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "health_connect_sync_periodic"
    }
}
