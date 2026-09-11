package com.locke.app.data.remote

import com.locke.app.data.local.entity.AnalyticsEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to Locke's own backend (`backend/`, see `src/routes/analytics.js`) over plain
 * HTTP(S) + JSON, authenticated as this device via [DeviceIdentityRepository]. See
 * `di/AnalyticsModule.kt` for the binding.
 */
@Singleton
class HttpAnalyticsApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val deviceIdentity: DeviceIdentityRepository,
) : AnalyticsApiClient {

    override suspend fun uploadEvents(events: List<AnalyticsEventEntity>): Boolean = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext true
        try {
            val body = JSONObject().put(
                "events",
                JSONArray().apply {
                    events.forEach { event ->
                        put(
                            JSONObject()
                                .put("name", event.name)
                                .put("properties", JSONObject(event.propertiesJson))
                                .put("clientTimestampEpochMillis", event.clientTimestampEpochMillis),
                        )
                    }
                },
            )
            val request = Request.Builder()
                .url("${BackendConfig.BASE_URL}/analytics/events")
                .addHeader("Authorization", deviceIdentity.authHeader())
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(request).execute().use { response -> response.isSuccessful }
        } catch (e: Exception) {
            // Offline, backend down, malformed properties JSON, whatever -- the caller
            // just leaves these events queued for the next flush, same as
            // AccountabilityRepository's own push failures. Never crash on a failed
            // analytics upload.
            false
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
