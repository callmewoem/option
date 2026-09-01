package com.habitsfirst.androidclone.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the two premade blocklists current by re-downloading [PremadeBlocklists.REMOTE_URLS]
 * and writing the result to [PremadeBlocklists.cacheFile], run periodically by
 * `BlocklistRefreshWorker`. Each fetch is independent -- one list failing (offline, upstream
 * down, a bad response) leaves the other's cached copy untouched, and the app keeps using
 * its last successfully fetched copy (or the bundled seed, if none has ever succeeded) either way.
 */
@Singleton
class PremadeBlocklistFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val appContext: Context,
) {
    /** Refreshes every premade list; returns how many of them updated successfully. */
    suspend fun refreshAll(): Int = withContext(Dispatchers.IO) {
        PremadeBlocklists.REMOTE_URLS.count { (id, url) -> refreshOne(id, url) }
    }

    private fun refreshOne(id: String, url: String): Boolean {
        val domains = runCatching { download(url) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return false
        val target = PremadeBlocklists.cacheFile(appContext, id)
        val written = runCatching { writeAtomically(target, domains) }.isSuccess
        if (written) PremadeBlocklists.invalidateCache(id)
        return written
    }

    private fun download(url: String): Set<String> {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} fetching $url")
            val body = response.body?.string() ?: throw IOException("Empty response fetching $url")
            return body.lineSequence().mapNotNull(::parseHostsLine).toSet()
        }
    }

    /**
     * Pulls the domain out of one line of a hosts-format file ("0.0.0.0 example.com", with
     * an optional trailing "# comment"), or treats the whole line as a bare domain if it
     * isn't in that format -- so a plain one-domain-per-line source parses just as well.
     */
    private fun parseHostsLine(rawLine: String): String? {
        val line = rawLine.substringBefore('#').trim()
        if (line.isEmpty()) return null
        val tokens = line.split(WHITESPACE)
        val domain = if (tokens.size >= 2 && IP_ADDRESS.matches(tokens[0])) tokens[1] else tokens[0]
        return domain.trim().lowercase().takeIf { it.contains('.') }
    }

    /** Writes to a sibling temp file and renames over [target], so a crash or a killed
     * process mid-write never leaves a truncated list behind. */
    private fun writeAtomically(target: File, domains: Set<String>) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.bufferedWriter().use { writer ->
            domains.forEach { domain ->
                writer.write(domain)
                writer.newLine()
            }
        }
        if (!temp.renameTo(target)) throw IOException("Couldn't replace ${target.path}")
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val IP_ADDRESS = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    }
}
