package com.habitsfirst.androidclone.service

import android.app.usage.UsageStatsManager
import android.content.Context
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** One [AppUsageSyncer.sync] run's outcome for a single habit -- a row in the Diagnostics screen's table. */
data class AppUsageHabitSyncResult(
    val habitId: Long,
    val habitName: String,
    val packageName: String,
    val targetMinutes: Int,
    /** What was stored for today before this run. */
    val previousStoredMinutes: Int,
    /** What [UsageStatsManager] reports for today, read fresh by this run. */
    val liveComputedMinutes: Int,
    /** Null on success. Set if [HabitRepository.setProgress] itself threw for this habit. */
    val writeError: String?,
)

/**
 * The full outcome of one [AppUsageSyncer.sync] run.
 *
 * [candidateCount] is how many habits [sync] actually considered ([results].size);
 * compare it against Diagnostics' separate "every APP_USAGE_MINUTES habit that
 * exists" count to catch a habit silently excluded by the candidate filter (no
 * target app saved, wrong type, etc.) -- something an error message alone can't
 * reveal, since a filtered-out habit is never an error, just silence.
 */
data class AppUsageSyncReport(
    val ranAtEpochMillis: Long,
    val hasUsageAccess: Boolean,
    val results: List<AppUsageHabitSyncResult>,
    /** Set only if the run couldn't even get as far as reading candidate habits -- e.g. no [UsageStatsManager] on this device. */
    val fatalError: String?,
) {
    val candidateCount: Int get() = results.size
}

/**
 * The actual "read today's per-app foreground time, write it as habit progress" logic
 * behind [UsageTrackingWorker], pulled out into its own injectable class for one reason:
 * so Settings -> Diagnostics can run and inspect it directly, synchronously, from the UI
 * thread's coroutine scope -- bypassing WorkManager (and Logcat) entirely -- instead of
 * only ever being able to guess at what a background run did.
 *
 * Every run, from any caller, records its outcome via
 * [PreferencesRepository.recordUsageSyncOutcome] so Diagnostics can also show what the
 * *automatic* (periodic or one-off) runs have been doing, not just manual ones.
 */
@Singleton
class AppUsageSyncer @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val context: Context,
) {
    suspend fun sync(): AppUsageSyncReport {
        val hasUsageAccess = PermissionUtils.hasUsageAccess(context)
        val ranAt = System.currentTimeMillis()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            val error = "UsageStatsManager unavailable on this device"
            preferencesRepository.recordUsageSyncOutcome(habitCount = 0, error = error)
            return AppUsageSyncReport(ranAt, hasUsageAccess, emptyList(), error)
        }

        val appUsageHabits = habitRepository.getAppUsageHabitsOnce()
        val startOfDay = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val stats = usageStatsManager.queryAndAggregateUsageStats(startOfDay, ranAt)

        val results = appUsageHabits.map { habit ->
            // getAppUsageHabitsOnce() already filters out a null targetPackageName --
            // orEmpty() is just so a broken invariant shows up as "no usage found" for
            // an empty-string package rather than crashing this whole run.
            val packageName = habit.targetPackageName.orEmpty()
            val foregroundMillis = stats[packageName]?.totalTimeInForeground ?: 0L
            val liveMinutes = (foregroundMillis / 60_000L).toInt()
            val previous = habitRepository.getProgressOnce(habit.id)
            val writeError = runCatching {
                habitRepository.setProgress(habit.id, liveMinutes, habit.targetValue)
            }.exceptionOrNull()?.let { it.message ?: it::class.simpleName ?: "Unknown error" }
            AppUsageHabitSyncResult(
                habitId = habit.id,
                habitName = habit.name,
                packageName = packageName,
                targetMinutes = habit.targetValue,
                previousStoredMinutes = previous,
                liveComputedMinutes = liveMinutes,
                writeError = writeError,
            )
        }

        val firstWriteError = results.firstOrNull { it.writeError != null }
        preferencesRepository.recordUsageSyncOutcome(
            habitCount = results.size,
            error = firstWriteError?.let { "\"${it.habitName}\": ${it.writeError}" },
        )
        return AppUsageSyncReport(ranAt, hasUsageAccess, results, fatalError = null)
    }
}
