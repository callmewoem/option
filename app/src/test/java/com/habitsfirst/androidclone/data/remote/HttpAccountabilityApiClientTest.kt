package com.habitsfirst.androidclone.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.DailySummary
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guards the "fail fast, never crash" contract from [AccountabilityApiClient]'s doc:
 * with no backend base URL configured (the default, since none ships with the app),
 * every call must surface [AccountabilityApiException.NoBackendConfigured] instead of
 * throwing an NPE or silently hitting a hardcoded host. The [OkHttpClient] passed in is
 * never actually used in this state -- [requireBaseUrl] short-circuits before any
 * network call is attempted.
 */
class HttpAccountabilityApiClientTest {

    private fun newClient(tempDir: File): HttpAccountabilityApiClient {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        return HttpAccountabilityApiClient(OkHttpClient(), PreferencesRepository(dataStore))
    }

    @Test
    fun `createPairingCode fails fast with no backend configured`() = withTempDir { tempDir ->
        val client = newClient(tempDir)
        runBlocking {
            try {
                client.createPairingCode()
                fail("expected NoBackendConfigured")
            } catch (e: AccountabilityApiException.NoBackendConfigured) {
                // expected
            }
        }
    }

    @Test
    fun `addBuddy fails fast with no backend configured`() = withTempDir { tempDir ->
        val client = newClient(tempDir)
        runBlocking {
            try {
                client.addBuddy("SOME-CODE")
                fail("expected NoBackendConfigured")
            } catch (e: AccountabilityApiException.NoBackendConfigured) {
                // expected
            }
        }
    }

    @Test
    fun `fetchBuddySummaries fails fast with no backend configured`() = withTempDir { tempDir ->
        val client = newClient(tempDir)
        runBlocking {
            try {
                client.fetchBuddySummaries()
                fail("expected NoBackendConfigured")
            } catch (e: AccountabilityApiException.NoBackendConfigured) {
                // expected
            }
        }
    }

    @Test
    fun `pushDailySummary surfaces NoBackendConfigured as a failed Result rather than throwing`() = withTempDir { tempDir ->
        val client = newClient(tempDir)
        runBlocking {
            val result = client.pushDailySummary(DailySummary("2026-09-05", 1, 2, 3))
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is AccountabilityApiException.NoBackendConfigured)
        }
    }

    @Test
    fun `a blank base URL is treated the same as unset`() = withTempDir { tempDir ->
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        val preferencesRepository = PreferencesRepository(dataStore)
        runBlocking { preferencesRepository.setAccountabilityBaseUrl("   ") }
        val client = HttpAccountabilityApiClient(OkHttpClient(), preferencesRepository)

        runBlocking {
            try {
                client.createPairingCode()
                fail("expected NoBackendConfigured")
            } catch (e: AccountabilityApiException.NoBackendConfigured) {
                // expected
            }
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
