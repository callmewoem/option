package com.habitsfirst.androidclone.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the Health Connect client for the habit types that can sync
 * automatically: steps, workout minutes, and sleep hours (see
 * [com.habitsfirst.androidclone.service.HealthConnectSyncWorker]).
 *
 * Nothing here requests permissions -- that has to happen from an Activity via
 * [androidx.health.connect.client.PermissionController.createRequestPermissionResultContract],
 * wired up in Settings -- this only reads once they're already granted, and quietly
 * reads as zero otherwise so callers never need a separate "is this set up" branch.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Null when the Health Connect provider isn't installed/available on this device. */
    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val isAvailable: Boolean get() = client != null

    suspend fun hasPermissions(): Boolean {
        val client = client ?: return false
        return runCatching {
            client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
        }.getOrDefault(false)
    }

    /** Today's step count so far, or 0 if unavailable, ungranted, or the read failed. */
    suspend fun todaySteps(): Int {
        val client = client ?: return 0
        if (!hasPermissions()) return 0
        return runCatching {
            val result = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), todayRange()))
            (result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
        }.getOrDefault(0)
    }

    /** Total workout minutes so far today, or 0 if unavailable, ungranted, or the read failed. */
    suspend fun workoutMinutesToday(): Int {
        val client = client ?: return 0
        if (!hasPermissions()) return 0
        return runCatching {
            val result = client.aggregate(
                AggregateRequest(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), todayRange()),
            )
            (result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL] ?: Duration.ZERO).toMinutes().toInt()
        }.getOrDefault(0)
    }

    /**
     * Total sleep hours over the trailing 24 hours, or 0 if unavailable, ungranted, or the
     * read failed. Uses a trailing window rather than [todayRange] because a sleep session
     * that started before midnight would otherwise be mostly missed by a midnight-anchored
     * query.
     *
     * Deliberately reads raw [SleepSessionRecord]s and sums their durations itself rather
     * than going through [HealthConnectClient.aggregate] the way [todaySteps] and
     * [workoutMinutesToday] do -- `SleepSessionRecord.SLEEP_DURATION_TOTAL` aggregation is
     * unreliable on the pinned alpha `health-connect-client` version and comes back empty
     * even when sessions exist and permission is granted, which otherwise made a
     * SLEEP_HOURS habit's progress look permanently stuck at zero.
     */
    suspend fun recentSleepHours(): Int {
        val client = client ?: return 0
        if (!hasPermissions()) return 0
        return runCatching {
            val now = Instant.now()
            val windowStart = now.minus(Duration.ofHours(24))
            val sessions = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(windowStart, now)),
            ).records
            val total = sessions.fold(Duration.ZERO) { acc, session ->
                // Clip to the window in case a session starts before it -- readRecords
                // returns any record that overlaps the filter, not just ones fully inside it.
                val start = maxOf(session.startTime, windowStart)
                val end = minOf(session.endTime, now)
                if (end.isAfter(start)) acc.plus(Duration.between(start, end)) else acc
            }
            total.toHours().toInt()
        }.getOrDefault(0)
    }

    private fun todayRange(): TimeRangeFilter {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        return TimeRangeFilter.between(startOfDay, Instant.now())
    }

    companion object {
        /**
         * Read-only, matching the `android.permission.health.READ_STEPS`,
         * `READ_EXERCISE`, and `READ_SLEEP` manifest entries.
         */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )
    }
}
