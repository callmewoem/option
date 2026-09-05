package com.habitsfirst.androidclone.data.remote

import com.habitsfirst.androidclone.data.remote.dto.toAccountabilityBuddy
import com.habitsfirst.androidclone.data.remote.dto.toDailySummary
import com.habitsfirst.androidclone.data.remote.dto.toJson
import com.habitsfirst.androidclone.data.remote.dto.toPairingCode
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import com.habitsfirst.androidclone.domain.model.AccountabilityBuddy
import com.habitsfirst.androidclone.domain.model.DailySummary
import com.habitsfirst.androidclone.domain.model.PairingCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * Talks to a user-configured accountability-buddy backend over plain HTTP(S) + JSON.
 * There is no default backend baked in -- [PreferencesRepository.accountabilityBaseUrl]
 * must be set in Settings first, or every call here fails fast with
 * [AccountabilityApiException.NoBackendConfigured] rather than hitting a hardcoded host
 * or throwing an NPE. A configured-but-unreachable backend (the common case today,
 * since none is hosted anywhere yet) surfaces as [AccountabilityApiException.Network]
 * instead of crashing the caller -- see `di/AccountabilityModule.kt` for the binding.
 *
 * Endpoints assumed of the backend (all under the configured base URL, all JSON):
 * `POST /pairing-codes`, `POST /buddies` (`{"code": ...}`), `POST /daily-summary`,
 * `GET /buddies`. No backend implementing these exists yet -- this is client-side
 * scaffolding for one, not a working integration.
 */
@Singleton
class HttpAccountabilityApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesRepository: PreferencesRepository,
) : AccountabilityApiClient {

    override suspend fun createPairingCode(): PairingCode = withContext(Dispatchers.IO) {
        val base = requireBaseUrl()
        val json = executeJson(Request.Builder().url("$base/pairing-codes").post(EMPTY_JSON_BODY).build())
        try {
            json.toPairingCode()
        } catch (e: Exception) {
            throw AccountabilityApiException.Api("Unexpected response creating a pairing code.")
        }
    }

    override suspend fun addBuddy(code: String): AccountabilityBuddy = withContext(Dispatchers.IO) {
        val base = requireBaseUrl()
        val body = JSONObject().put("code", code)
        val json = executeJson(
            Request.Builder()
                .url("$base/buddies")
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
            val base = requireBaseUrl()
            executeRaw(
                Request.Builder()
                    .url("$base/daily-summary")
                    .post(summary.toJson().toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
            )
            Unit
        }
    }

    override suspend fun fetchBuddySummaries(): List<AccountabilityBuddy> = withContext(Dispatchers.IO) {
        val base = requireBaseUrl()
        val text = executeRaw(Request.Builder().url("$base/buddies").get().build())
        try {
            val array = JSONArray(text)
            (0 until array.length()).map { i -> array.getJSONObject(i).toAccountabilityBuddy() }
        } catch (e: Exception) {
            throw AccountabilityApiException.Api("Unexpected response listing buddies.")
        }
    }

    private suspend fun requireBaseUrl(): String =
        preferencesRepository.accountabilityBaseUrl.first()
            ?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: throw AccountabilityApiException.NoBackendConfigured

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
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val EMPTY_JSON_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)
    }
}
