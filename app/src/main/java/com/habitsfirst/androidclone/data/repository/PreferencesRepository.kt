package com.habitsfirst.androidclone.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small pieces of app state that don't belong in Room: onboarding progress and the
 * cached streak count (recomputed lazily, cached here so the home screen can render
 * it instantly).
 */
@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val CACHED_STREAK = intPreferencesKey("cached_streak")
        val CACHED_STREAK_DATE = stringPreferencesKey("cached_streak_date")
        val THEME_VARIANT = stringPreferencesKey("theme_variant")
        val UNLOCKED_THEME_VARIANTS = stringSetPreferencesKey("unlocked_theme_variants")
        val GRACE_TOKEN_COUNT = intPreferencesKey("grace_token_count")
        val TASK_SKIP_TOKEN_COUNT = intPreferencesKey("task_skip_token_count")
        val LAST_LOOTBOX_AWARDED_DATE = stringPreferencesKey("last_lootbox_awarded_date")
        val PENALTY_LOCKED_UNTIL_EPOCH_MILLIS = longPreferencesKey("penalty_locked_until_epoch_millis")
        val BEDTIME_LOCK_ENABLED = booleanPreferencesKey("bedtime_lock_enabled")
        val BEDTIME_START = stringPreferencesKey("bedtime_start") // "HH:mm"
        val BEDTIME_END = stringPreferencesKey("bedtime_end") // "HH:mm"
        val MORNING_TODO_REMINDER_ENABLED = booleanPreferencesKey("morning_todo_reminder_enabled")
        val MORNING_TODO_REMINDER_TIME = stringPreferencesKey("morning_todo_reminder_time") // "HH:mm"
    }

    val isOnboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    val areNotificationsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    val cachedStreak: Flow<Pair<Int, String?>> = dataStore.data.map {
        (it[Keys.CACHED_STREAK] ?: 0) to it[Keys.CACHED_STREAK_DATE]
    }

    suspend fun setCachedStreak(days: Int, forDate: String) {
        dataStore.edit {
            it[Keys.CACHED_STREAK] = days
            it[Keys.CACHED_STREAK_DATE] = forDate
        }
    }

    // -- Theme (lootbox-unlockable) --------------------------------------------------

    val selectedThemeVariantId: Flow<String> = dataStore.data.map { it[Keys.THEME_VARIANT] ?: "" }

    suspend fun setSelectedThemeVariantId(variantId: String) {
        dataStore.edit { it[Keys.THEME_VARIANT] = variantId }
    }

    /** Every variant the user has unlocked from the lootbox. "MOSS" is always available. */
    val unlockedThemeVariantIds: Flow<Set<String>> =
        dataStore.data.map { (it[Keys.UNLOCKED_THEME_VARIANTS] ?: emptySet()) + "MOSS" }

    suspend fun unlockThemeVariant(variantId: String) {
        dataStore.edit {
            it[Keys.UNLOCKED_THEME_VARIANTS] = (it[Keys.UNLOCKED_THEME_VARIANTS] ?: emptySet()) + variantId
        }
    }

    // -- Lootbox rewards ----------------------------------------------------------------

    val graceTokenCount: Flow<Int> = dataStore.data.map { it[Keys.GRACE_TOKEN_COUNT] ?: 0 }
    val taskSkipTokenCount: Flow<Int> = dataStore.data.map { it[Keys.TASK_SKIP_TOKEN_COUNT] ?: 0 }

    suspend fun addGraceTokens(count: Int) {
        dataStore.edit { it[Keys.GRACE_TOKEN_COUNT] = (it[Keys.GRACE_TOKEN_COUNT] ?: 0) + count }
    }

    suspend fun addTaskSkipTokens(count: Int) {
        dataStore.edit { it[Keys.TASK_SKIP_TOKEN_COUNT] = (it[Keys.TASK_SKIP_TOKEN_COUNT] ?: 0) + count }
    }

    /** Returns true and consumes a token if one was available. */
    suspend fun consumeGraceToken(): Boolean {
        var consumed = false
        dataStore.edit {
            val current = it[Keys.GRACE_TOKEN_COUNT] ?: 0
            if (current > 0) {
                it[Keys.GRACE_TOKEN_COUNT] = current - 1
                consumed = true
            }
        }
        return consumed
    }

    suspend fun consumeTaskSkipToken(): Boolean {
        var consumed = false
        dataStore.edit {
            val current = it[Keys.TASK_SKIP_TOKEN_COUNT] ?: 0
            if (current > 0) {
                it[Keys.TASK_SKIP_TOKEN_COUNT] = current - 1
                consumed = true
            }
        }
        return consumed
    }

    /** Guards against awarding more than one lootbox per calendar day. */
    val lastLootboxAwardedDate: Flow<String?> = dataStore.data.map { it[Keys.LAST_LOOTBOX_AWARDED_DATE] }

    suspend fun setLastLootboxAwardedDate(date: String) {
        dataStore.edit { it[Keys.LAST_LOOTBOX_AWARDED_DATE] = date }
    }

    // -- Penalties ------------------------------------------------------------------

    /** Blocked apps stay locked until this instant even after habits are complete. 0 = no active penalty. */
    val penaltyLockedUntilEpochMillis: Flow<Long> =
        dataStore.data.map { it[Keys.PENALTY_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L }

    suspend fun extendPenaltyLock(untilEpochMillis: Long) {
        dataStore.edit {
            val current = it[Keys.PENALTY_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L
            it[Keys.PENALTY_LOCKED_UNTIL_EPOCH_MILLIS] = maxOf(current, untilEpochMillis)
        }
    }

    suspend fun clearPenaltyLock() {
        dataStore.edit { it[Keys.PENALTY_LOCKED_UNTIL_EPOCH_MILLIS] = 0L }
    }

    // -- Bedtime lock -----------------------------------------------------------------

    data class BedtimeSettings(val enabled: Boolean, val start: String, val end: String)

    val bedtimeSettings: Flow<BedtimeSettings> = dataStore.data.map {
        BedtimeSettings(
            enabled = it[Keys.BEDTIME_LOCK_ENABLED] ?: false,
            start = it[Keys.BEDTIME_START] ?: "22:30",
            end = it[Keys.BEDTIME_END] ?: "06:30",
        )
    }

    suspend fun setBedtimeSettings(enabled: Boolean, start: String, end: String) {
        dataStore.edit {
            it[Keys.BEDTIME_LOCK_ENABLED] = enabled
            it[Keys.BEDTIME_START] = start
            it[Keys.BEDTIME_END] = end
        }
    }

    // -- Daily todo reminder -----------------------------------------------------------

    data class MorningReminderSettings(val enabled: Boolean, val time: String)

    val morningTodoReminderSettings: Flow<MorningReminderSettings> = dataStore.data.map {
        MorningReminderSettings(
            enabled = it[Keys.MORNING_TODO_REMINDER_ENABLED] ?: true,
            time = it[Keys.MORNING_TODO_REMINDER_TIME] ?: "08:00",
        )
    }

    suspend fun setMorningTodoReminderSettings(enabled: Boolean, time: String) {
        dataStore.edit {
            it[Keys.MORNING_TODO_REMINDER_ENABLED] = enabled
            it[Keys.MORNING_TODO_REMINDER_TIME] = time
        }
    }
}
