package com.habitsfirst.androidclone.data.billing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.SubscriptionTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [StubEntitlementRepository] derives `isPremium` purely from the locally stored tier and
 * expiry: false with nothing purchased, false for [SubscriptionTier.NONE], true for a
 * purchased tier that hasn't expired, and false again once it has.
 */
class StubEntitlementRepositoryTest {

    private fun newRepository(tempDir: File): StubEntitlementRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        return StubEntitlementRepository(PreferencesRepository(dataStore))
    }

    @Test
    fun `isPremium is false with no purchase recorded`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newRepository(tempDir)
            assertFalse(repository.isPremium())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `isPremium is false after recording a NONE tier purchase`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newRepository(tempDir)
            repository.recordPurchase(SubscriptionTier.NONE, null)
            assertFalse(repository.isPremium())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `isPremium is true for a lifetime purchase with no expiry`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newRepository(tempDir)
            repository.recordPurchase(SubscriptionTier.LIFETIME, null)
            assertTrue(repository.isPremium())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `isPremium is true for a subscription that has not expired yet`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newRepository(tempDir)
            repository.recordPurchase(SubscriptionTier.MONTHLY, System.currentTimeMillis() + 60_000)
            assertTrue(repository.isPremium())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `isPremium is false for a subscription that already expired`() = runBlocking {
        val tempDir = createTempDir()
        try {
            val repository = newRepository(tempDir)
            repository.recordPurchase(SubscriptionTier.MONTHLY, System.currentTimeMillis() - 60_000)
            assertFalse(repository.isPremium())
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
