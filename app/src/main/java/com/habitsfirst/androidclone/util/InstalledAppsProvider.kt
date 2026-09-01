package com.habitsfirst.androidclone.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.habitsfirst.androidclone.domain.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lists apps installed on the device so the user can pick which ones to lock behind
 * their habits. Excludes this app itself and apps with no launcher entry point
 * (nothing useful to "block" there).
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(launchIntent, 0)

        resolved
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = appInfo.loadLabel(packageManager).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Today's per-app foreground minutes, for the app picker's "Most used" sort.
     * Returns an empty map (never throws) if usage access hasn't been granted --
     * [android.app.usage.UsageStatsManager] just yields no data rather than failing,
     * so callers see every app tie at zero and the list falls back to its existing order.
     */
    suspend fun getTodayUsageMinutes(): Map<String, Int> = withContext(Dispatchers.IO) {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@withContext emptyMap()

        val startOfDay = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val now = System.currentTimeMillis()

        usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
            .mapValues { (_, stats) -> (stats.totalTimeInForeground / 60_000L).toInt() }
    }

    fun getAppLabel(packageName: String): String = try {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        appInfo.loadLabel(context.packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
