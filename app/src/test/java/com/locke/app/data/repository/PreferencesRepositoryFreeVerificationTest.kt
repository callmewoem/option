package com.locke.app.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The free tier's monthly photo-verification allowance
 * ([PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH],
 * [PreferencesRepository.consumeFreeVerificationIfAvailable]/[PreferencesRepository.freeVerificationsRemaining])
 * -- the client-side half of the same quota `backend/src/services/entitlement.js`
 * enforces server-side; both must agree on the "resets every month, capped at N,
 * only spent by a call that actually completed" contract this guards.
 */
class PreferencesRepositoryFreeVerificationTest {

    private fun newRepository(tempDir: File): PreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        return PreferencesRepository(dataStore)
    }

    @Test
    fun `a fresh month starts with the full allowance`() = withTempDir { tempDir ->
        val repository = newRepository(tempDir)
        runBlocking {
            assertEquals(
                PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH,
                repository.freeVerificationsRemaining("2026-09").first(),
            )
        }
    }

    @Test
    fun `consuming spends one check and is reflected in what's remaining`() = withTempDir { tempDir ->
        val repository = newRepository(tempDir)
        runBlocking {
            assertTrue(repository.consumeFreeVerificationIfAvailable("2026-09"))
            assertEquals(
                PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH - 1,
                repository.freeVerificationsRemaining("2026-09").first(),
            )
        }
    }

    @Test
    fun `consuming past the monthly cap fails and remaining floors at zero`() = withTempDir { tempDir ->
        val repository = newRepository(tempDir)
        runBlocking {
            repeat(PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH) {
                assertTrue(repository.consumeFreeVerificationIfAvailable("2026-09"))
            }
            assertFalse(repository.consumeFreeVerificationIfAvailable("2026-09"))
            assertEquals(0, repository.freeVerificationsRemaining("2026-09").first())
        }
    }

    @Test
    fun `a new calendar month resets the allowance even after last month was fully spent`() = withTempDir { tempDir ->
        val repository = newRepository(tempDir)
        runBlocking {
            repeat(PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH) {
                repository.consumeFreeVerificationIfAvailable("2026-09")
            }
            assertEquals(0, repository.freeVerificationsRemaining("2026-09").first())

            // A new month, same stored count -- ignored, since it belongs to September.
            assertEquals(
                PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH,
                repository.freeVerificationsRemaining("2026-10").first(),
            )
            assertTrue(repository.consumeFreeVerificationIfAvailable("2026-10"))
        }
    }

    @Suppress("SameParameterValue")
    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = File.createTempFile("locke_free_verification_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
