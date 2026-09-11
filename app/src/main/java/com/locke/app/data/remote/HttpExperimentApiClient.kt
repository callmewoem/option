package com.locke.app.data.remote

import com.locke.app.data.remote.dto.toExperimentAssignments
import com.locke.app.domain.model.ExperimentAssignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to Locke's own backend (`backend/`, see `src/routes/experiments.js`) over
 * plain HTTP(S) + JSON, authenticated as this device via [DeviceIdentityRepository].
 * See `di/ExperimentModule.kt` for the binding. Mirrors
 * [HttpAccountabilityApiClient]'s shape.
 */
@Singleton
class HttpExperimentApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val deviceIdentity: DeviceIdentityRepository,
) : ExperimentApiClient {

    override suspend fun fetchAssignments(): List<ExperimentAssignment> = withContext(Dispatchers.IO) {
        val authHeader = try {
            deviceIdentity.authHeader()
        } catch (e: BackendApiException.Network) {
            throw ExperimentApiException.Network(e.message ?: "Couldn't reach the backend.", e)
        } catch (e: BackendApiException.Api) {
            throw ExperimentApiException.Api(e.message ?: "Couldn't register this device.")
        }

        val request = Request.Builder()
            .url("${BackendConfig.BASE_URL}/experiments")
            .addHeader("Authorization", authHeader)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ExperimentApiException.Api("Couldn't fetch experiment assignments (HTTP ${response.code}).")
                }
                try {
                    JSONObject(text).getJSONArray("experiments").toExperimentAssignments()
                } catch (e: Exception) {
                    throw ExperimentApiException.Api("Unexpected response fetching experiment assignments.")
                }
            }
        } catch (e: IOException) {
            throw ExperimentApiException.Network("Couldn't reach the backend.", e)
        }
    }
}
