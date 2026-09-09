package com.habitsfirst.androidclone.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.verification.WakaTimeApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically refreshes progress for [com.habitsfirst.androidclone.domain.model.HabitType.WAKATIME_CODING_MINUTES]
 * habits from the user's own WakaTime account, mirroring [HealthConnectSyncWorker]'s
 * approach exactly: read an absolute today-so-far value (total coding minutes) and
 * overwrite the stored progress with it. A no-op if no WakaTime API key is set --
 * [WakaTimeApiClient] reads as 0 then, same as [com.habitsfirst.androidclone.data.healthconnect.HealthConnectManager]
 * with no permission granted.
 */
@HiltWorker
class WakaTimeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val wakaTimeApiClient: WakaTimeApiClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val habits = habitRepository.getWakaTimeHabitsOnce()
        if (habits.isEmpty()) return Result.success()

        val minutesToday = wakaTimeApiClient.todayCodingMinutes()
        for (habit in habits) {
            habitRepository.setProgress(habit.id, minutesToday, habit.targetValue)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "wakatime_sync_periodic"
        const val ONE_OFF_NAME = "wakatime_sync_one_off"
    }
}
