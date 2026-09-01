package com.habitsfirst.androidclone.data.billing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.SubscriptionTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the current hardcoding in [StubEntitlementRepository]: `isPremium` must report
 * `true` no matter what tier is actually stored, so that the seam is a no-op today and
 * only starts mattering once a real Play-Billing-backed implementation replaces it.
 */
class StubEntitlementRepositoryTest {

    private fun newRepository(tempDir: File): StubEntitlementRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        return StubEntitlementRepository(PreferencesRepository(dataStore))
    }

    @Test
    fun `isPremium is true with no purchase recorded`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newRepository(tempDir)
            assertTrue(repository.isPremium())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `isPremium is true even after recording a NONE tier purchase`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newRepository(tempDir)
            repository.recordPurchase(SubscriptionTier.NONE, null)
            assertTrue(repository.isPremium())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Suppress("SameParameterValue")
    private fun createTempDir(): File = File.createTempFile("locke_billing_test", "").apply {
        delete()
        mkdirs()
    }
}
