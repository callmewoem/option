package com.habitsfirst.androidclone.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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
}
