package com.habitsfirst.androidclone.util

import android.content.Context
import com.habitsfirst.androidclone.domain.model.BlockListSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The two bundled "blanket" domain lists -- porn and social media. Each starts from a
 * comprehensive snapshot bundled under `assets/blocklists/` (see those files for
 * provenance/curation notes) and is kept current by [PremadeBlocklistFetcher], which
 * periodically re-downloads the same upstream source and writes the result to
 * [cacheFile] -- that cached copy, when present, always wins over the bundled seed.
 * Either way the domains are cached in memory for the life of the process, invalidated
 * by [invalidateCache] whenever a fetch replaces the on-disk copy.
 *
 * Each list is a plain list of domains (one per line, '#' comments allowed); matching
 * (in [com.habitsfirst.androidclone.data.repository.UrlBlockRepository]) walks up a
 * page's host label by label, so an entry like "pornhub.com" also catches
 * "www.pornhub.com" but a listed "www.example.com" does not by itself cover bare
 * "example.com" -- most entries here are already bare domains for that reason.
 */
object PremadeBlocklists {
    const val ID_PORN = "premade_porn"
    const val ID_SOCIAL = "premade_social"

    const val NAME_PORN = "Porn & adult content"
    const val NAME_SOCIAL = "Social media"

    private val ASSET_PATHS = mapOf(
        ID_PORN to "blocklists/porn.txt",
        ID_SOCIAL to "blocklists/social.txt",
    )

    /** Upstream hosts-format sources [PremadeBlocklistFetcher] re-syncs from -- MIT licensed,
     * see https://github.com/Sinfonietta/hostfiles. */
    val REMOTE_URLS = mapOf(
        ID_PORN to "https://raw.githubusercontent.com/Sinfonietta/hostfiles/master/pornography-hosts",
        ID_SOCIAL to "https://raw.githubusercontent.com/Sinfonietta/hostfiles/master/social-hosts",
    )

    private val NAMES = mapOf(ID_PORN to NAME_PORN, ID_SOCIAL to NAME_SOCIAL)

    /** The fixed ids of the two premade lists, seeded once by [com.habitsfirst.androidclone.data.repository.UrlBlockRepository]. */
    val ids: Set<String> = ASSET_PATHS.keys

    fun nameFor(id: String): String = NAMES[id] ?: id

    fun sourceFor(id: String): BlockListSource = when (id) {
        ID_PORN -> BlockListSource.PREMADE_PORN
        ID_SOCIAL -> BlockListSource.PREMADE_SOCIAL
        else -> BlockListSource.CUSTOM
    }

    /** Where a freshly fetched copy of [id]'s list is written; read back in [domainsFor] ahead of the bundled asset. */
    fun cacheFile(context: Context, id: String): File =
        File(context.filesDir, "blocklists/$id.txt")

    private val cache = mutableMapOf<String, Set<String>>()

    private val _refreshSignal = MutableStateFlow(0L)

    /** Ticks whenever [invalidateCache] runs, so [com.habitsfirst.androidclone.data.repository.UrlBlockRepository]'s
     * `combine()`-based flows -- which otherwise only re-emit when the block-list/domain
     * tables themselves change -- also recompute after a background refresh replaces a
     * premade list's domains, instead of only catching up on the next unrelated settings change. */
    val refreshSignal: StateFlow<Long> = _refreshSignal.asStateFlow()

    /** Drops [id]'s in-memory copy so the next [domainsFor] call re-reads from disk -- called by [PremadeBlocklistFetcher] after it writes a fresh [cacheFile]. */
    @Synchronized
    fun invalidateCache(id: String) {
        cache.remove(id)
        _refreshSignal.value = System.currentTimeMillis()
    }

    @Synchronized
    fun domainsFor(context: Context, id: String): Set<String> {
        cache[id]?.let { return it }
        val domains = readCacheFile(context, id) ?: readBundledAsset(context, id)
        cache[id] = domains
        return domains
    }

    private fun readCacheFile(context: Context, id: String): Set<String>? {
        val file = cacheFile(context, id)
        if (!file.isFile) return null
        return runCatching { file.bufferedReader().use { it.readLines() } }
            .getOrNull()
            ?.toCleanDomainSet()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun readBundledAsset(context: Context, id: String): Set<String> {
        val path = ASSET_PATHS[id] ?: return emptySet()
        return runCatching {
            context.assets.open(path).bufferedReader().use { it.readLines() }
        }.getOrDefault(emptyList()).toCleanDomainSet()
    }

    private fun List<String>.toCleanDomainSet(): Set<String> = map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toSet()
}
