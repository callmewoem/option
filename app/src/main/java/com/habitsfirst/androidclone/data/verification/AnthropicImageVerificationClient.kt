package com.habitsfirst.androidclone.data.verification

import android.util.Base64
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
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
 * Asks a Claude vision model whether a submitted photo satisfies a habit's completion
 * rules (a free-text description, an example photo, or both).
 */
@Singleton
class AnthropicImageVerificationClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesRepository: PreferencesRepository,
) : ImageVerificationClient {

    override suspend fun verify(request: VerificationRequest): VerificationResult =
        withContext(Dispatchers.IO) {
            val apiKey = preferencesRepository.anthropicApiKey.first()?.takeIf { it.isNotBlank() }
                ?: throw ImageVerificationException.MissingApiKey

            val body = buildRequestBody(request)
            val httpRequest = Request.Builder()
                .url(MESSAGES_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val responseBody = try {
                okHttpClient.newCall(httpRequest).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw ImageVerificationException.Api(extractErrorMessage(text) ?: "Verification failed (HTTP ${response.code}).")
                    }
                    text
                }
            } catch (e: IOException) {
                throw ImageVerificationException.Network("Couldn't reach the verification service.", e)
            }

            parseVerdict(responseBody)
        }

    private fun buildRequestBody(request: VerificationRequest): JSONObject {
        val content = JSONArray()

        val rules = buildString {
            append("Habit: \"${request.habitName}\".\n")
            if (!request.description.isNullOrBlank()) {
                append("What counts as done, in the user's own words: \"${request.description.trim()}\".\n")
            }
            if (request.exampleImage != null) {
                append("An example photo of what \"done\" looks like is attached below, labeled EXAMPLE.\n")
            }
            if (request.description.isNullOrBlank() && request.exampleImage == null) {
                append("The user gave no description, only judge against the habit name.\n")
            }
        }
        content.put(textBlock(rules))

        request.exampleImage?.let { example ->
            content.put(textBlock("EXAMPLE photo:"))
            content.put(imageBlock(example))
        }

        content.put(textBlock("SUBMITTED photo, just taken by the user as today's proof:"))
        content.put(imageBlock(request.submittedImage))
        content.put(
            textBlock(
                "Decide if the submitted photo is genuine, current proof that the habit was completed. " +
                    "Reply with ONLY a compact JSON object, no other text: " +
                    "{\"approved\": true or false, \"reasoning\": \"one short, friendly sentence\"}.",
            ),
        )

        val message = JSONObject()
            .put("role", "user")
            .put("content", content)

        return JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 300)
            .put("system", SYSTEM_PROMPT)
            .put("messages", JSONArray().put(message))
    }

    private fun textBlock(text: String) = JSONObject().put("type", "text").put("text", text)

    private fun imageBlock(bytes: ByteArray) = JSONObject()
        .put("type", "image")
        .put(
            "source",
            JSONObject()
                .put("type", "base64")
                .put("media_type", "image/jpeg")
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
        )

    private fun parseVerdict(responseBody: String): VerificationResult {
        val root = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            throw ImageVerificationException.Api("Unexpected response from the verification service.")
        }
        val text = root.optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: throw ImageVerificationException.Api("The verification service returned no answer.")

        val verdict = try {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            require(start in 0..end)
            JSONObject(text.substring(start, end + 1))
        } catch (e: Exception) {
            throw ImageVerificationException.Api("Couldn't understand the verification result.")
        }
        return VerificationResult(
            approved = verdict.optBoolean("approved", false),
            reasoning = verdict.optString("reasoning").ifBlank { "No reasoning given." },
        )
    }

    private fun extractErrorMessage(body: String): String? =
        try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

    companion object {
        private const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val MODEL = "claude-haiku-4-5-20251001"
        private const val SYSTEM_PROMPT =
            "You verify photo proof for a habit-tracking app that locks distracting apps until the " +
                "user's habits are done for the day. Be reasonably strict but fair: approve genuine, " +
                "current evidence the habit was just completed, and reject photos that are unrelated, " +
                "reused/old-looking, screenshots of other photos, or otherwise unconvincing. Always " +
                "reply with only the requested JSON object."
    }
}
