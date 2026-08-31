package com.habitsfirst.androidclone.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
}
