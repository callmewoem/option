package com.habitsfirst.androidclone.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exercises the hard-mode friend-lock/PIN behavior added to [PreferencesRepository], same DataStore-backed
 * pattern as [LockoutRepositoryTest].
 */
class PreferencesRepositoryHardModeTest {

    @Test
    fun `enabling with a duration and no pin locks the toggle until that duration passes`() = runBlocking {
        val prefs = newPreferencesRepository()
        val now = System.currentTimeMillis()

        assertTrue(prefs.setHardModeEnabled(true, lockDurationDays = 7, pin = null, nowEpochMillis = now))

        val lock = prefs.hardModeFriendLock.first()
        assertFalse(lock.isPermanent)
        assertFalse(lock.pinSet)
        assertTrue(lock.isActive(now))
        assertFalse(lock.isActive(now + 7 * DAY_MILLIS + 1))
    }

    @Test
    fun `turning off before the lock duration passes is rejected with no pin set`() = runBlocking {
        val prefs = newPreferencesRepository()
        val now = System.currentTimeMillis()
        prefs.setHardModeEnabled(true, lockDurationDays = 7, pin = null, nowEpochMillis = now)

        val applied = prefs.setHardModeEnabled(false, nowEpochMillis = now + DAY_MILLIS)
        assertFalse(applied)
        assertTrue(prefs.isHardModeEnabled.first())
    }

    @Test
    fun `turning off succeeds once the lock duration has passed`() = runBlocking {
        val prefs = newPreferencesRepository()
        val now = System.currentTimeMillis()
        prefs.setHardModeEnabled(true, lockDurationDays = 7, pin = null, nowEpochMillis = now)

        val applied = prefs.setHardModeEnabled(false, nowEpochMillis = now + 7 * DAY_MILLIS + 1)
        assertTrue(applied)
        assertFalse(prefs.isHardModeEnabled.first())
    }

    @Test
    fun `a permanent lock with no pin is rejected`() = runBlocking {
        val prefs = newPreferencesRepository()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { prefs.setHardModeEnabled(true, lockDurationDays = null, pin = null) }
        }
    }

    @Test
    fun `a permanent lock with a pin never expires on its own but the pin still unlocks it`() = runBlocking {
        val prefs = newPreferencesRepository()
        val now = System.currentTimeMillis()
        prefs.setHardModeEnabled(true, lockDurationDays = null, pin = "1234", nowEpochMillis = now)

        val lock = prefs.hardModeFriendLock.first()
        assertTrue(lock.isPermanent)
        assertTrue(lock.isActive(now + 365L * DAY_MILLIS))

        val result = prefs.unlockHardModeWithPin("1234", nowEpochMillis = now)
        assertEquals(PreferencesRepository.HardModePinResult.Unlocked, result)
        assertFalse(prefs.isHardModeEnabled.first())
        assertFalse(prefs.hardModeFriendLock.first().isActive(now))
    }

    @Test
    fun `the correct pin unlocks early regardless of remaining duration`() = runBlocking {
        val prefs = newPreferencesRepository()
        val now = System.currentTimeMillis()
        prefs.setHardModeEnabled(true, lockDurationDays = 30, pin = "9876", nowEpochMillis = now)

        val result = prefs.unlockHardModeWithPin("9876", nowEpochMillis = now + DAY_MILLIS)
        assertEquals(PreferencesRepository.HardModePinResult.Unlocked, result)
        assertFalse(prefs.isHardModeEnabled.first())
    }

    @Test
    fun `a wrong pin is rejected and counts down remaining attempts`() = runBlocking {
        val prefs = newPreferencesRepository()
        val now = System.currentTimeMillis()
        prefs.setHardModeEnabled(true, lockDurationDays = 30, pin = "1111", nowEpochMillis = now)

        val result = prefs.unlockHardModeWithPin("0000", nowEpochMillis = now)
        assertEquals(PreferencesRepository.HardModePinResult.WrongPin(attemptsRemaining = 4), result)
        assertTrue(prefs.isHardModeEnabled.first())
    }

    @Test
    fun `exhausting a round of wrong guesses starts a cooldown that blocks further attempts, even correct ones`() = runBlocking {
        val prefs = newPreferencesRepository()
        val now = System.currentTimeMillis()
        prefs.setHardModeEnabled(true, lockDurationDays = 30, pin = "1111", nowEpochMillis = now)

        repeat(PreferencesRepository.HARD_MODE_PIN_ATTEMPTS_PER_ROUND) {
            prefs.unlockHardModeWithPin("0000", nowEpochMillis = now)
        }

        val throttled = prefs.unlockHardModeWithPin("1111", nowEpochMillis = now)
        assertTrue(throttled is PreferencesRepository.HardModePinResult.TooManyAttempts)
        assertTrue(prefs.isHardModeEnabled.first())

        val retryAt = (throttled as PreferencesRepository.HardModePinResult.TooManyAttempts).retryAtEpochMillis
        val afterCooldown = prefs.unlockHardModeWithPin("1111", nowEpochMillis = retryAt + 1)
        assertEquals(PreferencesRepository.HardModePinResult.Unlocked, afterCooldown)
    }

    @Test
    fun `guessing with no pin ever set reports NoPinSet`() = runBlocking {
        val prefs = newPreferencesRepository()
        prefs.setHardModeEnabled(true, lockDurationDays = 7, pin = null)

        assertEquals(PreferencesRepository.HardModePinResult.NoPinSet, prefs.unlockHardModeWithPin("0000"))
    }

    private fun newPreferencesRepository(): PreferencesRepository {
        val tempDir = createTempDir()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        return PreferencesRepository(dataStore)
    }

    @Suppress("SameParameterValue")
    private fun createTempDir(): File = File.createTempFile("locke_hard_mode_test", "").apply {
        delete()
        mkdirs()
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
