package com.habitsfirst.androidclone.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exercises [LockoutRepository] against a real DataStore, same pattern as
 * [LimitedUnblockRepositoryTest].
 */
class LockoutRepositoryTest {

    @Test
    fun `no lockout ever started is never active`() = runBlocking {
        val repository = newLockoutRepository()
        assertFalse(repository.isLockoutActiveNow())
        assertEquals(0L, repository.lockoutUntilEpochMillis.first())
    }

    @Test
    fun `starting a lockout makes it active until roughly now plus the requested minutes`() = runBlocking {
        val repository = newLockoutRepository()
        val before = System.currentTimeMillis()
        repository.startLockout(30)
        val after = System.currentTimeMillis()

        val until = repository.lockoutUntilEpochMillis.first()
        assertTrue(until >= before + 30 * 60_000L)
        assertTrue(until <= after + 30 * 60_000L)
        assertTrue(repository.isLockoutActiveNow(nowEpochMillis = before + 60_000L))
    }

    @Test
    fun `isLockoutActiveNow is false once the target instant has passed`() = runBlocking {
        val repository = newLockoutRepository()
        repository.startLockout(15)
        val until = repository.lockoutUntilEpochMillis.first()

        assertFalse(repository.isLockoutActiveNow(nowEpochMillis = until + 1))
    }

    @Test
    fun `cancelling an active lockout clears it`() = runBlocking {
        val repository = newLockoutRepository()
        repository.startLockout(60)
        assertTrue(repository.isLockoutActiveNow())

        repository.cancelLockout()
        assertFalse(repository.isLockoutActiveNow())
        assertEquals(0L, repository.lockoutUntilEpochMillis.first())
    }

    private fun newLockoutRepository(): LockoutRepository {
        val tempDir = createTempDir()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        return LockoutRepository(PreferencesRepository(dataStore))
    }

    @Suppress("SameParameterValue")
    private fun createTempDir(): File = File.createTempFile("locke_lockout_test", "").apply {
        delete()
        mkdirs()
    }
}
