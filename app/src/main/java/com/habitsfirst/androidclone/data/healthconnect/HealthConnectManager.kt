package com.habitsfirst.androidclone.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the Health Connect client for the two habit types that can sync
 * automatically: steps and exercise minutes (see [com.habitsfirst.androidclone.service.HealthConnectSyncWorker]).
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

    /** Today's total logged exercise-session duration in minutes, or 0 if unavailable/ungranted. */
    suspend fun todayExerciseMinutes(): Int {
        val client = client ?: return 0
        if (!hasPermissions()) return 0
        return runCatching {
            val result = client.aggregate(
                AggregateRequest(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), todayRange()),
            )
            (result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL] ?: Duration.ZERO).toMinutes().toInt()
        }.getOrDefault(0)
    }

    private fun todayRange(): TimeRangeFilter {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        return TimeRangeFilter.between(startOfDay, Instant.now())
    }

    companion object {
        /** Read-only, matching the two `android.permission.health.*` entries in the manifest. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )
    }
}
