package com.habitsfirst.androidclone.data.verification

import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks whether a GitHub user has contributed today, for
 * [com.habitsfirst.androidclone.domain.model.HabitType.GITHUB_CONTRIBUTION] habits (see
 * [com.habitsfirst.androidclone.service.GithubSyncWorker]). Works with just a public
 * username -- the user's own personal access token (optional, set in Settings) is added
 * as a bearer token when present, which raises GitHub's unauthenticated rate limit and,
 * called against the authenticated user's own username, also surfaces their private
 * activity. Reads once, quietly as `false` on any error, same shape as
 * [WakaTimeApiClient].
 */
@Singleton
class GitHubApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesRepository: PreferencesRepository,
) {
    /** True if [username] has any contribution-like public event (or private, with a token) since local midnight today. */
    suspend fun hasContributedToday(username: String): Boolean = withContext(Dispatchers.IO) {
        val token = preferencesRepository.githubToken.first()?.takeIf { it.isNotBlank() }
        runCatching {
            val requestBuilder = Request.Builder()
                .url("https://api.github.com/users/$username/events?per_page=100")
                .addHeader("Accept", "application/vnd.github+json")
            if (token != null) requestBuilder.addHeader("Authorization", "Bearer $token")

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val events = JSONArray(response.body?.string().orEmpty())
                val startOfToday = LocalDate.now().atStartOfDay(ZoneOffset.systemDefault()).toInstant()
                (0 until events.length()).any { i ->
                    val event = events.getJSONObject(i)
                    val type = event.optString("type")
                    val createdAt = event.optString("created_at")
                    type in CONTRIBUTION_EVENT_TYPES && isOnOrAfter(createdAt, startOfToday.toEpochMilli())
                }
            }
        }.getOrDefault(false)
    }

    private fun isOnOrAfter(isoTimestamp: String, epochMillisFloor: Long): Boolean =
        runCatching { OffsetDateTime.parse(isoTimestamp).toInstant().toEpochMilli() >= epochMillisFloor }.getOrDefault(false)

    companion object {
        /** Event types that read as "did something today" rather than passive activity (e.g. WatchEvent, ForkEvent). */
        private val CONTRIBUTION_EVENT_TYPES = setOf(
            "PushEvent", "PullRequestEvent", "PullRequestReviewEvent", "IssuesEvent",
            "IssueCommentEvent", "CreateEvent", "CommitCommentEvent",
        )
    }
}
