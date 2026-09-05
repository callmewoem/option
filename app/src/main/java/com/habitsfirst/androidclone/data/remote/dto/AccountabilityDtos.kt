package com.habitsfirst.androidclone.data.remote.dto

import com.habitsfirst.androidclone.domain.model.AccountabilityBuddy
import com.habitsfirst.androidclone.domain.model.BuddyConnectionStatus
import com.habitsfirst.androidclone.domain.model.DailySummary
import com.habitsfirst.androidclone.domain.model.PairingCode
import org.json.JSONObject

/**
 * JSON (de)serialization for the accountability-buddy backend's request/response
 * bodies, matching the plain `org.json` style used in
 * `data/verification/AnthropicImageVerificationClient.kt` -- this repo has no
 * Retrofit/Moshi/kotlinx.serialization. Kept separate from `HttpAccountabilityApiClient`
 * so the mapping logic is unit-testable without any networking involved.
 */

fun DailySummary.toJson(): JSONObject = JSONObject()
    .put("date", date)
    .put("habitsCompleted", habitsCompleted)
    .put("totalHabits", totalHabits)
    .put("currentStreak", currentStreak)

/** Throws [org.json.JSONException] on a malformed body -- callers wrap this in a try/catch and surface [com.habitsfirst.androidclone.data.remote.AccountabilityApiException.Api]. */
fun JSONObject.toDailySummary(): DailySummary = DailySummary(
    date = getString("date"),
    habitsCompleted = optInt("habitsCompleted"),
    totalHabits = optInt("totalHabits"),
    currentStreak = optInt("currentStreak"),
)

/** Throws [org.json.JSONException] on a malformed body -- see [toDailySummary]. */
fun JSONObject.toPairingCode(): PairingCode = PairingCode(code = getString("code"))

/** Throws [org.json.JSONException] on a malformed body -- see [toDailySummary]. */
fun JSONObject.toAccountabilityBuddy(): AccountabilityBuddy {
    val status = when (optString("status")) {
        "connected" -> BuddyConnectionStatus.Connected
        "error" -> BuddyConnectionStatus.Error(optString("statusMessage").ifBlank { "Sync failed." })
        else -> BuddyConnectionStatus.Pending
    }
    return AccountabilityBuddy(
        id = getString("id"),
        displayName = optString("displayName").ifBlank { "Buddy" },
        pairingCode = optString("pairingCode"),
        status = status,
        lastSummary = optJSONObject("lastSummary")?.toDailySummary(),
    )
}
