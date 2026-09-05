package com.habitsfirst.androidclone.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Enqueues the background work that keeps app-usage habit progress up to date. */
object WorkScheduler {

    fun scheduleUsageTracking(context: Context) {
        val constraints = Constraints.Builder().build()
        val request = PeriodicWorkRequestBuilder<UsageTrackingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UsageTrackingWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Requested when the user leaves a tracked app, so its habit updates sooner than 15 min. */
    fun requestUsageRefreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UsageTrackingWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UsageTrackingWorker.ONE_OFF_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Enqueues the periodic check that posts the "fill in today's todos" morning reminder. */
    fun scheduleMorningTodoReminder(context: Context) {
        val request = PeriodicWorkRequestBuilder<MorningReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MorningReminderWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Enqueues the periodic check that penalizes a missed proof-of-life check-in. */
    fun scheduleProofOfLifeCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<ProofOfLifeWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ProofOfLifeWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Enqueues the periodic sync of Steps/Workout Minutes/Sleep Hours habits from Health Connect. Only called once the user turns sync on in Settings (with permissions already granted). */
    fun scheduleHealthConnectSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HealthConnectSyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Stops the Health Connect sync worker -- called when the user turns sync back off in Settings. */
    fun cancelHealthConnectSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(HealthConnectSyncWorker.UNIQUE_PERIODIC_NAME)
    }

    /** Requested when Health Connect sync is on and progress needs to catch up sooner than the next 30-min tick -- see [requestUsageRefreshNow]. */
    fun requestHealthConnectRefreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            HealthConnectSyncWorker.ONE_OFF_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Enqueues the periodic check that posts the opt-in "N/7 days complete" weekly recap.
     * Only called once the user turns the digest on in Settings -- see [cancelWeeklyDigest].
     */
    fun scheduleWeeklyDigest(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeeklyDigestWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Stops the weekly digest worker -- called when the user turns the recap back off in Settings. */
    fun cancelWeeklyDigest(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeeklyDigestWorker.UNIQUE_PERIODIC_NAME)
    }

    /** Enqueues the periodic re-sync of the premade URL blocklists from their upstream source. */
    fun scheduleBlocklistRefresh(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<BlocklistRefreshWorker>(3, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BlocklistRefreshWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
