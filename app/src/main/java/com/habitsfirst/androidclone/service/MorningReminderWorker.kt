package com.habitsfirst.androidclone.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.HabitsFirstApp
import com.habitsfirst.androidclone.MainActivity
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.util.DateProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime

/**
 * Runs on the same ~15-minute cadence as [UsageTrackingWorker] and posts the daily
 * "fill in today's todos" reminder once it's within [REMINDER_WINDOW_MINUTES] of the
 * user's configured time -- no exact-alarm machinery needed since a notification a few
 * minutes late doesn't matter the way a wake-up check-in would.
 */
@HiltWorker
class MorningReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = preferencesRepository.morningTodoReminderSettings.first()
        if (!settings.enabled) return Result.success()

        val today = DateProvider.todayString()
        if (preferencesRepository.lastMorningReminderSentDate.first() == today) return Result.success()

        val target = runCatching { LocalTime.parse(settings.time) }.getOrDefault(LocalTime.of(8, 0))
        val now = LocalTime.now()
        val minutesPast = Duration.between(target, now).toMinutes()
        if (minutesPast < 0 || minutesPast > REMINDER_WINDOW_MINUTES) return Result.success()

        preferencesRepository.setLastMorningReminderSentDate(today)
        postNotification()
        return Result.success()
    }

    private fun postNotification() {
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, HabitsFirstApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.morning_reminder_title))
            .setContentText(applicationContext.getString(R.string.morning_reminder_body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "morning_todo_reminder_periodic"
        private const val REMINDER_WINDOW_MINUTES = 20L
        private const val NOTIFICATION_ID = 1001
    }
}
