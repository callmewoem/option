package com.locke.app.data.billing

import com.locke.app.data.remote.BackendApiException
import com.locke.app.data.remote.BackendConfig
import com.locke.app.data.remote.DeviceIdentityRepository
import com.locke.app.domain.model.SubscriptionTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What the backend reports this device's entitlement to be. */
data class VerifiedEntitlement(val tier: SubscriptionTier, val expiresAtEpochMillis: Long?)

/**
 * Talks to Locke's own backend (`backend/`, see `src/routes/billing.js`) to verify a
 * just-completed Play Billing purchase and to re-fetch this device's current
 * entitlement. Kept separate from [PlayBillingEntitlementRepository] so the plain HTTP
 * plumbing is independent of the Play Billing SDK calls around it.
 */
@Singleton
class BackendPurchaseVerifier @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val deviceIdentity: DeviceIdentityRepository,
) {
    /** Verifies [purchaseToken] for [productId] with the backend, which checks it against the real Play Developer API and records the resulting entitlement. Null on any failure -- callers keep whatever entitlement was last known rather than crash. */
    suspend fun verifyPurchase(productId: String, purchaseToken: String): VerifiedEntitlement? =
        runCatching {
            val body = JSONObject().put("productId", productId).put("purchaseToken", purchaseToken)
            val json = post("$BASE/purchases/verify", body)
            json.toVerifiedEntitlement()
        }.getOrNull()

    /** This device's current entitlement as last recorded by the backend. Null on any failure. */
    suspend fun fetchEntitlement(): VerifiedEntitlement? =
        runCatching {
            val json = get("$BASE/entitlement")
            json.toVerifiedEntitlement()
        }.getOrNull()

    private fun JSONObject.toVerifiedEntitlement(): VerifiedEntitlement = VerifiedEntitlement(
        tier = SubscriptionTier.fromId(optString("tier")),
        expiresAtEpochMillis = if (has("expiresAtEpochMillis") && !isNull("expiresAtEpochMillis")) getLong("expiresAtEpochMillis") else null,
    )

    private suspend fun authHeader(): String = try {
        deviceIdentity.authHeader()
    } catch (e: BackendApiException) {
        throw IOException(e.message, e)
    }

    private suspend fun post(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", authHeader())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        execute(request)
    }

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).addHeader("Authorization", authHeader()).get().build()
        execute(request)
    }

    private fun execute(request: Request): JSONObject {
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Backend responded HTTP ${response.code}: $text")
            return JSONObject(text)
        }
    }

    companion object {
        private val BASE = BackendConfig.BASE_URL
    }
}
