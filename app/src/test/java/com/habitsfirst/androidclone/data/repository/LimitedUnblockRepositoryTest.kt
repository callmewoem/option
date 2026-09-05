package com.habitsfirst.androidclone.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * [LimitedUnblockRepository.computeEffectiveWindowMinutes] is the streak-bonus math that
 * used to just be a hardcoded 60; these guard it directly since exercising the full
 * [LimitedUnblockRepository]/[HabitRepository] path needs a real Room database. The
 * [PreferencesRepository] persistence side (defaults + clamping) is covered separately
 * against a real DataStore, same pattern as
 * [com.habitsfirst.androidclone.data.billing.StubEntitlementRepositoryTest].
 */
class LimitedUnblockRepositoryTest {

    @Test
    fun `no bonus returns the base window unchanged`() {
        assertEquals(60, LimitedUnblockRepository.computeEffectiveWindowMinutes(baseMinutes = 60, bonusMinutes = 0))
    }

    @Test
    fun `streak bonus is added to the base window`() {
        // A 5-day streak at 5 bonus minutes per day.
        assertEquals(85, LimitedUnblockRepository.computeEffectiveWindowMinutes(baseMinutes = 60, bonusMinutes = 5 * 5))
    }

    @Test
    fun `an enormous streak bonus is capped at MAX_LIMITED_UNBLOCK_WINDOW_MINUTES`() {
        // A 100-day streak at 30 bonus minutes per day would be 3060 minutes uncapped.
        assertEquals(
            PreferencesRepository.MAX_LIMITED_UNBLOCK_WINDOW_MINUTES,
            LimitedUnblockRepository.computeEffectiveWindowMinutes(baseMinutes = 60, bonusMinutes = 100 * 30),
        )
    }

    @Test
    fun `a negative bonus never shrinks the window below the base`() {
        assertEquals(60, LimitedUnblockRepository.computeEffectiveWindowMinutes(baseMinutes = 60, bonusMinutes = -30))
    }

    @Test
    fun `preferences default to a 60-minute window with the streak bonus off`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newPreferencesRepository(tempDir)
            val settings = repository.limitedUnblockWindowSettings.first()
            assertEquals(PreferencesRepository.DEFAULT_LIMITED_UNBLOCK_WINDOW_MINUTES, settings.windowMinutes)
            assertEquals(false, settings.streakBonusEnabled)
            assertEquals(PreferencesRepository.DEFAULT_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY, settings.streakBonusMinutesPerDay)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `setLimitedUnblockWindowMinutes clamps to the 5 to 480 range`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newPreferencesRepository(tempDir)
            repository.setLimitedUnblockWindowMinutes(1)
            assertEquals(5, repository.limitedUnblockWindowSettings.first().windowMinutes)

            repository.setLimitedUnblockWindowMinutes(9000)
            assertEquals(480, repository.limitedUnblockWindowSettings.first().windowMinutes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `setLimitedUnblockStreakBonus clamps minutes per day to 0 to 30`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newPreferencesRepository(tempDir)
            repository.setLimitedUnblockStreakBonus(enabled = true, minutesPerDay = -5)
            var settings = repository.limitedUnblockWindowSettings.first()
            assertEquals(true, settings.streakBonusEnabled)
            assertEquals(0, settings.streakBonusMinutesPerDay)

            repository.setLimitedUnblockStreakBonus(enabled = true, minutesPerDay = 999)
            settings = repository.limitedUnblockWindowSettings.first()
            assertEquals(30, settings.streakBonusMinutesPerDay)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun newPreferencesRepository(tempDir: File): PreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        return PreferencesRepository(dataStore)
    }

    @Suppress("SameParameterValue")
    private fun createTempDir(): File = File.createTempFile("locke_limited_unblock_test", "").apply {
        delete()
        mkdirs()
    }
}
