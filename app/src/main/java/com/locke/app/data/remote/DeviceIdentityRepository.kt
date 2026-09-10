package com.locke.app.data.remote

import com.locke.app.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This install's identity with Locke's own backend. There's no login -- the first time
 * any backend call needs to authenticate, [authHeader] lazily registers this device
 * (`POST /v1/devices`, see `backend/src/routes/devices.js`) and caches the returned
 * bearer token via [PreferencesRepository.setDeviceIdentity], so every call after the
 * first (across every backend-facing client, and every app launch) reuses it instead of
 * registering again.
 *
 * Every backend-facing client -- [HttpAccountabilityApiClient],
 * [com.locke.app.data.verification.BackendImageVerificationClient],
 * [com.locke.app.data.billing.PlayBillingEntitlementRepository] -- shares
 * this one identity, since they're all really just "this device" talking to the same
 * backend.
 */
@Singleton
class DeviceIdentityRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesRepository: PreferencesRepository,
) {
    /** Guards against two concurrent callers both registering a fresh (and now orphaned) device identity. */
    private val registrationMutex = Mutex()

    /** The `Authorization` header value to attach to an authenticated backend request, registering this device first if needed. */
    suspend fun authHeader(): String = "Bearer ${deviceToken()}"

    private suspend fun deviceToken(): String {
        preferencesRepository.deviceIdentity.first()?.let { return it.token }
        return registrationMutex.withLock {
            // Re-check: another caller may have finished registering while this one
            // was waiting on the lock.
            preferencesRepository.deviceIdentity.first()?.let { return@withLock it.token }
            registerDevice()
        }
    }

    private suspend fun registerDevice(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BackendConfig.BASE_URL}/devices")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        val json = try {
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw BackendApiException.Api("Couldn't register this device (HTTP ${response.code}).")
                }
                JSONObject(text)
            }
        } catch (e: IOException) {
            throw BackendApiException.Network("Couldn't reach Locke's backend.", e)
        } catch (e: BackendApiException) {
            throw e
        } catch (e: Exception) {
            throw BackendApiException.Api("Unexpected response registering this device.")
        }

        val id = json.getString("deviceId")
        val token = json.getString("token")
        preferencesRepository.setDeviceIdentity(id, token)
        token
    }
}
