package com.locke.app.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.domain.model.DailySummary
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guards the "fail fast, never crash" contract from [AccountabilityApiClient]'s doc:
 * with the backend unreachable (as it always is from this JVM-only unit test sandbox --
 * [BackendConfig.BASE_URL] points at a placeholder host with no DNS entry), every call
 * must surface [AccountabilityApiException.Network] instead of throwing an unchecked
 * exception. Device registration ([DeviceIdentityRepository]) is on the same critical
 * path (every call registers the device first if it hasn't yet), so this also exercises
 * that it fails the same clean way rather than crashing differently from everything
 * downstream of it.
 */
class HttpAccountabilityApiClientTest {

    private fun newClient(tempDir: File): HttpAccountabilityApiClient {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        val okHttpClient = OkHttpClient()
        val preferencesRepository = PreferencesRepository(dataStore)
        val deviceIdentity = DeviceIdentityRepository(okHttpClient, preferencesRepository)
        return HttpAccountabilityApiClient(okHttpClient, deviceIdentity)
    }

    @Test
    fun `createPairingCode surfaces Network when the backend is unreachable`() = withTempDir { tempDir ->
        val client = newClient(tempDir)
        runBlocking {
            try {
                client.createPairingCode()
                fail("expected AccountabilityApiException.Network")
            } catch (e: AccountabilityApiException.Network) {
                // expected
            }
        }
    }

    @Test
    fun `addBuddy surfaces Network when the backend is unreachable`() = withTempDir { tempDir ->
        val client = newClient(tempDir)
        runBlocking {
            try {
                client.addBuddy("SOME-CODE")
                fail("expected AccountabilityApiException.Network")
            } catch (e: AccountabilityApiException.Network) {
                // expected
            }
        }
    }

    @Test
    fun `fetchBuddySummaries surfaces Network when the backend is unreachable`() = withTempDir { tempDir ->
        val client = newClient(tempDir)
        runBlocking {
            try {
                client.fetchBuddySummaries()
                fail("expected AccountabilityApiException.Network")
            } catch (e: AccountabilityApiException.Network) {
                // expected
            }
        }
    }

    @Test
    fun `pushDailySummary surfaces Network as a failed Result rather than throwing`() = withTempDir { tempDir ->
        val client = newClient(tempDir)
        runBlocking {
            val result = client.pushDailySummary(DailySummary("2026-09-05", 1, 2, 3))
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is AccountabilityApiException.Network)
        }
    }

    @Suppress("SameParameterValue")
    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = File.createTempFile("locke_accountability_test", "").apply {
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
