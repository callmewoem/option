package com.locke.app.data.remote.dto

import com.locke.app.domain.model.ExperimentAssignment
import org.json.JSONArray
import org.json.JSONObject

/** JSON parsing for `GET /v1/experiments`'s `{experiments: [{key, variant, value}, ...]}` body. */

/** Throws [org.json.JSONException] on a malformed body -- callers wrap this in a try/catch and surface [com.locke.app.data.remote.ExperimentApiException.Api]. */
fun JSONObject.toExperimentAssignment(): ExperimentAssignment = ExperimentAssignment(
    key = getString("key"),
    variant = getString("variant"),
    value = getString("value"),
)

fun JSONArray.toExperimentAssignments(): List<ExperimentAssignment> =
    (0 until length()).map { i -> getJSONObject(i).toExperimentAssignment() }
