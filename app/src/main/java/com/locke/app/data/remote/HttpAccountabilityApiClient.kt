package com.locke.app.data.remote

import com.locke.app.data.remote.dto.toAccountabilityBuddy
import com.locke.app.data.remote.dto.toJson
import com.locke.app.data.remote.dto.toPairingCode
import com.locke.app.domain.model.AccountabilityBuddy
import com.locke.app.domain.model.DailySummary
import com.locke.app.domain.model.PairingCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to Locke's own backend (`backend/`, see `src/routes/buddies.js`) over plain
 * HTTP(S) + JSON, authenticated as this device via [DeviceIdentityRepository]. See
 * `di/AccountabilityModule.kt` for the binding.
 *
 * Endpoints (all under [BackendConfig.BASE_URL], all JSON): `POST /pairing-codes`,
 * `POST /buddies` (`{"code": ...}`), `POST /daily-summary`, `GET /buddies`.
 */
@Singleton
class HttpAccountabilityApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val deviceIdentity: DeviceIdentityRepository,
) : AccountabilityApiClient {

    override suspend fun createPairingCode(): PairingCode = withContext(Dispatchers.IO) {
        val json = executeJson(authedRequest("$BASE/pairing-codes").post(EMPTY_JSON_BODY).build())
        try {
            json.toPairingCode()
        } catch (e: Exception) {
            throw AccountabilityApiException.Api("Unexpected response creating a pairing code.")
        }
    }

    override suspend fun addBuddy(code: String): AccountabilityBuddy = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", code)
        val json = executeJson(
            authedRequest("$BASE/buddies")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        try {
            json.toAccountabilityBuddy()
        } catch (e: Exception) {
            throw AccountabilityApiException.Api("Unexpected response adding that buddy.")
        }
    }

    override suspend fun pushDailySummary(summary: DailySummary): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            executeRaw(
                authedRequest("$BASE/daily-summary")
                    .post(summary.toJson().toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
            )
            Unit
        }
    }

    override suspend fun fetchBuddySummaries(): List<AccountabilityBuddy> = withContext(Dispatchers.IO) {
        val text = executeRaw(authedRequest("$BASE/buddies").get().build())
        try {
            val array = JSONArray(text)
            (0 until array.length()).map { i -> array.getJSONObject(i).toAccountabilityBuddy() }
        } catch (e: Exception) {
            throw AccountabilityApiException.Api("Unexpected response listing buddies.")
        }
    }

    /** A [Request.Builder] pre-seeded with this device's `Authorization` header -- registers the device first if this is the first call ever made. */
    private suspend fun authedRequest(url: String): Request.Builder =
        Request.Builder().url(url).addHeader("Authorization", authHeader())

    private suspend fun authHeader(): String = try {
        deviceIdentity.authHeader()
    } catch (e: BackendApiException.Network) {
        throw AccountabilityApiException.Network(e.message ?: "Couldn't reach the accountability backend.", e)
    } catch (e: BackendApiException.Api) {
        throw AccountabilityApiException.Api(e.message ?: "Couldn't register this device.")
    }

    private fun executeJson(request: Request): JSONObject =
        try {
            JSONObject(executeRaw(request))
        } catch (e: AccountabilityApiException) {
            throw e
        } catch (e: Exception) {
            throw AccountabilityApiException.Api("Unexpected response from the accountability backend.")
        }

    /** Runs [request]; non-2xx and IO failures both surface as typed [AccountabilityApiException]s -- never left to crash the caller. */
    private fun executeRaw(request: Request): String =
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 402) {
                        // The backend's own error body (see routes/buddies.js) already
                        // names whose free-tier buddy limit was hit -- this generic
                        // fallback is only for the rare case that body didn't parse.
                        throw AccountabilityApiException.Api(
                            extractErrorMessage(text) ?: "Reached the free plan's buddy limit. Upgrade to add more.",
                        )
                    }
                    throw AccountabilityApiException.Api(extractErrorMessage(text) ?: "Request failed (HTTP ${response.code}).")
                }
                text
            }
        } catch (e: IOException) {
            throw AccountabilityApiException.Network("Couldn't reach the accountability backend.", e)
        }

    private fun extractErrorMessage(body: String): String? =
        try {
            JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

    companion object {
        private val BASE = BackendConfig.BASE_URL
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val EMPTY_JSON_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)
    }
}
