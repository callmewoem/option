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
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.util.DateProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Runs on the same ~15-minute cadence as [MorningReminderWorker] and posts an opt-in
 * once-a-week recap -- "5/7 days complete this week, best streak 4 days" -- once it's
 * within [REMINDER_WINDOW_MINUTES] of the user's configured day/time. Built entirely
 * from existing [HabitRepository] stats queries (the same ones the Stats tab's heatmap
 * uses) so it needs no new tracked data of its own -- just a nudge for people who
 * otherwise wouldn't open that tab.
 */
@HiltWorker
class WeeklyDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
    private val habitRepository: HabitRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = preferencesRepository.weeklyDigestSettings.first()
        val lastSentDate = preferencesRepository.lastWeeklyDigestSentDate.first()
        val today = LocalDate.now()

        if (!isDue(settings, lastSentDate, today, LocalTime.now())) return Result.success()

        val (completedDays, bestStreak) = buildWeekSummary(today)
        preferencesRepository.setLastWeeklyDigestSentDate(DateProvider.toDateString(today))
        postNotification(completedDays, bestStreak)
        return Result.success()
    }

    /**
     * The trailing 7 calendar days (today inclusive): how many were fully complete
     * (every active GATING habit done), and the longest run of complete days within
     * that window. The streak loop intentionally mirrors (rather than shares code
     * with) the Stats tab's own longest-streak-in-range calculation in
     * `HabitsViewModel.longestRun` -- pulling it into `HabitRepository` would touch a
     * file other in-flight units are also editing, which isn't worth it for a few
     * lines of pure date-walking logic.
     */
    private suspend fun buildWeekSummary(today: LocalDate): Pair<Int, Int> {
        val start = today.minusDays(WEEK_LOOKBACK_DAYS - 1)
        val scores = habitRepository.getDayScoresInRange(
            DateProvider.toDateString(start),
            DateProvider.toDateString(today),
        )

        var completedDays = 0
        var longestStreak = 0
        var currentStreak = 0
        var cursor = start
        while (!cursor.isAfter(today)) {
            val dayComplete = (scores[DateProvider.toDateString(cursor)] ?: 0f) >= 1f
            if (dayComplete) {
                completedDays++
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 0
            }
            cursor = cursor.plusDays(1)
        }
        return completedDays to longestStreak
    }

    private fun postNotification(completedDays: Int, bestStreak: Int) {
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
            .setContentTitle(applicationContext.getString(R.string.weekly_digest_title))
            .setContentText(applicationContext.getString(R.string.weekly_digest_body, completedDays, bestStreak))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "weekly_digest_periodic"
        private const val REMINDER_WINDOW_MINUTES = 20L
        private const val NOTIFICATION_ID = 1002
        private const val WEEK_LOOKBACK_DAYS = 7L

        /**
         * Pure gating check, extracted so it's testable without DataStore/WorkManager: due
         * when the digest is enabled, today matches the configured day of week, we're within
         * [REMINDER_WINDOW_MINUTES] after the configured time, and this week's recap hasn't
         * gone out yet. [lastSentDate] is the plain date the last digest was posted on (same
         * shape as [PreferencesRepository.lastMorningReminderSentDate]) -- since the
         * configured day of week only occurs once every 7 days, "already sent on today's
         * date" and "already sent this week" are the same check.
         */
        fun isDue(
            settings: PreferencesRepository.WeeklyDigestSettings,
            lastSentDate: String?,
            today: LocalDate,
            now: LocalTime,
        ): Boolean {
            if (!settings.enabled) return false
            if (today.dayOfWeek != settings.dayOfWeek) return false
            if (lastSentDate == DateProvider.toDateString(today)) return false

            val target = runCatching { LocalTime.parse(settings.time) }.getOrDefault(LocalTime.of(18, 0))
            val minutesPast = Duration.between(target, now).toMinutes()
            return minutesPast in 0..REMINDER_WINDOW_MINUTES
        }
    }
}
