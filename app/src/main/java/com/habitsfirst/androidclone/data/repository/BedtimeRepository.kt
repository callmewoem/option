package com.habitsfirst.androidclone.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A hard digital curfew: while enabled and inside the [start, end) window, blocked
 * apps stay locked no matter what -- not gated by habits, not bypassable by a grace
 * token. The window can wrap midnight (e.g. 22:30 -> 06:30).
 */
@Singleton
class BedtimeRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) {
    val settings: Flow<PreferencesRepository.BedtimeSettings> = preferencesRepository.bedtimeSettings

    suspend fun setBedtime(enabled: Boolean, start: String, end: String) {
        preferencesRepository.setBedtimeSettings(enabled, start, end)
    }

    suspend fun isWithinBedtimeWindowNow(): Boolean {
        val settings = preferencesRepository.bedtimeSettings.first()
        if (!settings.enabled) return false
        return isWithinWindow(LocalTime.now(), parseTime(settings.start), parseTime(settings.end))
    }

    internal fun isWithinWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) now >= start && now < end else now >= start || now < end

    private fun parseTime(value: String): LocalTime = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.MIDNIGHT)
}
