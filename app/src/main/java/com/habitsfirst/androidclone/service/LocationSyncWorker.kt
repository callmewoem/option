package com.habitsfirst.androidclone.service

import android.content.Context
import android.location.Location
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.data.location.DeviceLocationProvider
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.domain.model.Habit
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically checks the device's last-known location against every active
 * [com.habitsfirst.androidclone.domain.model.HabitType.VISIT_LOCATION] habit's saved
 * target, marking it done for today once the device has been within its radius --
 * mirroring [HealthConnectSyncWorker]'s "read once per tick" shape, except a location
 * check can only ever mark a habit *done*, never undo it (see
 * [HabitRepository.markDoneIfDetected]): driving away from the gym later in the day
 * shouldn't un-complete a habit that already registered as visited.
 */
@HiltWorker
class LocationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val locationProvider: DeviceLocationProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val here = locationProvider.lastKnownLocation() ?: return Result.success()
        val habits = habitRepository.getVisitLocationHabitsOnce()
        for (habit in habits) {
            if (isWithinTargetRadius(here, habit)) {
                habitRepository.markDoneIfDetected(habit.id)
            }
        }
        return Result.success()
    }

    private fun isWithinTargetRadius(here: Location, habit: Habit): Boolean {
        val lat = habit.targetLatitude ?: return false
        val lng = habit.targetLongitude ?: return false
        val radiusMeters = habit.targetRadiusMeters ?: Habit.DEFAULT_VISIT_LOCATION_RADIUS_METERS
        val results = FloatArray(1)
        Location.distanceBetween(here.latitude, here.longitude, lat, lng, results)
        return results[0] <= radiusMeters
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "location_sync_periodic"
        const val ONE_OFF_NAME = "location_sync_one_off"
    }
}
