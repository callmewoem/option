package com.locke.app.data.verification

import android.util.Base64
import com.locke.app.data.billing.EntitlementRepository
import com.locke.app.data.remote.BackendApiException
import com.locke.app.data.remote.BackendConfig
import com.locke.app.data.remote.DeviceIdentityRepository
import com.locke.app.data.repository.PreferencesRepository
import com.locke.app.util.DateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * Asks Locke's own backend (`backend/`, see `src/routes/verify.js`) whether a submitted
 * photo satisfies a habit's completion rules. Replaces the old
 * `AnthropicImageVerificationClient`, which called Claude straight from the device using
 * an API key the user had to paste into Settings -- that key now lives only in the
 * backend process's environment (`ANTHROPIC_API_KEY`), never inside the app.
 *
 * Free tier gets [PreferencesRepository.FREE_VERIFICATIONS_PER_MONTH] checks a month
 * before Premium is required -- checked here before the request is even sent (cheaper
 * than a round trip, and gives an instant, no-network answer), and the backend enforces
 * the same quota server-side for defense in depth against a modified client. The local
 * count is only spent once a check actually gets a verdict back, approved or rejected
 * (both cost the backend a real Anthropic call) -- not on a network/API failure that
 * never reached one; see [PreferencesRepository.consumeFreeVerificationIfAvailable]'s doc.
 */
@Singleton
class BackendImageVerificationClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val deviceIdentity: DeviceIdentityRepository,
    private val entitlementRepository: EntitlementRepository,
    private val preferencesRepository: PreferencesRepository,
) : ImageVerificationClient {

    override suspend fun verify(request: VerificationRequest): VerificationResult =
        withContext(Dispatchers.IO) {
            val isPremium = entitlementRepository.isPremium()
            val nowMonth = DateProvider.currentMonthString()
            if (!isPremium) {
                val remaining = preferencesRepository.freeVerificationsRemaining(nowMonth).first()
                if (remaining <= 0) throw ImageVerificationException.RequiresPremium
            }

            val authHeader = try {
                deviceIdentity.authHeader()
            } catch (e: BackendApiException.Network) {
                throw ImageVerificationException.Network(e.message ?: "Couldn't reach the verification service.", e)
            } catch (e: BackendApiException.Api) {
                throw ImageVerificationException.Api(e.message ?: "Couldn't register this device.")
            }

            val httpRequest = Request.Builder()
                .url("${BackendConfig.BASE_URL}/verify-photo")
                .addHeader("Authorization", authHeader)
                .post(buildRequestBody(request).toString().toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = try {
                okHttpClient.newCall(httpRequest).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if (response.code == 402) throw ImageVerificationException.RequiresPremium
                        throw ImageVerificationException.Api(extractErrorMessage(text) ?: "Verification failed (HTTP ${response.code}).")
                    }
                    text
                }
            } catch (e: IOException) {
                throw ImageVerificationException.Network("Couldn't reach the verification service.", e)
            }

            // A round trip that actually got a verdict back -- whether the photo was
            // approved or rejected, it cost the backend an Anthropic call, so it
            // counts against the free monthly allowance either way. Mirrors what the
            // backend itself just counted server-side (see routes/verify.js).
            if (!isPremium) preferencesRepository.consumeFreeVerificationIfAvailable(nowMonth)

            parseVerdict(responseBody)
        }

    private fun buildRequestBody(request: VerificationRequest): JSONObject =
        JSONObject()
            .put("habitName", request.habitName)
            .apply { request.description?.let { put("description", it) } }
            .apply { request.exampleImage?.let { put("exampleImageBase64", Base64.encodeToString(it, Base64.NO_WRAP)) } }
            .put("submittedImageBase64", Base64.encodeToString(request.submittedImage, Base64.NO_WRAP))

    private fun parseVerdict(responseBody: String): VerificationResult {
        val json = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            throw ImageVerificationException.Api("Unexpected response from the verification service.")
        }
        return VerificationResult(
            approved = json.optBoolean("approved", false),
            reasoning = json.optString("reasoning").ifBlank { "No reasoning given." },
        )
    }

    private fun extractErrorMessage(body: String): String? =
        try {
            JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
}
