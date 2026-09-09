package com.habitsfirst.androidclone.data.verification

import android.util.Base64
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads today's coding time from the user's own WakaTime account, for
 * [com.habitsfirst.androidclone.domain.model.HabitType.WAKATIME_CODING_MINUTES] habits
 * (see [com.habitsfirst.androidclone.service.WakaTimeSyncWorker]). Same shape as
 * [AnthropicImageVerificationClient]/[HealthConnectManager][com.habitsfirst.androidclone.data.healthconnect.HealthConnectManager]:
 * reads once, quietly as zero if no key is set or the request fails, so callers never
 * need a separate "is this set up" branch.
 */
@Singleton
class WakaTimeApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesRepository: PreferencesRepository,
) {
    /** Total coding minutes so far today, or 0 if no API key is set, the request fails, or the response can't be parsed. */
    suspend fun todayCodingMinutes(): Int = withContext(Dispatchers.IO) {
        val apiKey = preferencesRepository.wakaTimeApiKey.first()?.takeIf { it.isNotBlank() } ?: return@withContext 0
        runCatching {
            val credentials = Base64.encodeToString("$apiKey:".toByteArray(), Base64.NO_WRAP)
            val request = Request.Builder()
                .url(STATUS_BAR_URL)
                .addHeader("Authorization", "Basic $credentials")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use 0
                val body = response.body?.string().orEmpty()
                val totalSeconds = JSONObject(body)
                    .optJSONObject("data")
                    ?.optJSONObject("grand_total")
                    ?.optDouble("total_seconds", 0.0)
                    ?: 0.0
                (totalSeconds / 60.0).toInt()
            }
        }.getOrDefault(0)
    }

    companion object {
        private const val STATUS_BAR_URL = "https://wakatime.com/api/v1/users/current/status_bar/today"
    }
}
