package com.habitsfirst.androidclone.util

import android.content.Context
import com.habitsfirst.androidclone.domain.model.BlockListSource

/**
 * The two bundled "blanket" domain lists -- porn and social media -- read once from
 * `assets/blocklists/*.txt` and cached in memory for the life of the process. See
 * those files for provenance/curation notes. Each is a plain list of registrable
 * domains (one per line, '#' comments allowed); matching (in
 * [com.habitsfirst.androidclone.data.repository.UrlBlockRepository]) walks up a page's
 * host label by label, so a bundled entry like "pornhub.com" also catches
 * "www.pornhub.com".
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

    private val NAMES = mapOf(ID_PORN to NAME_PORN, ID_SOCIAL to NAME_SOCIAL)

    /** The fixed ids of the two premade lists, seeded once by [com.habitsfirst.androidclone.data.repository.UrlBlockRepository]. */
    val ids: Set<String> = ASSET_PATHS.keys

    fun nameFor(id: String): String = NAMES[id] ?: id

    fun sourceFor(id: String): BlockListSource = when (id) {
        ID_PORN -> BlockListSource.PREMADE_PORN
        ID_SOCIAL -> BlockListSource.PREMADE_SOCIAL
        else -> BlockListSource.CUSTOM
    }

    private val cache = mutableMapOf<String, Set<String>>()

    @Synchronized
    fun domainsFor(context: Context, id: String): Set<String> {
        cache[id]?.let { return it }
        val path = ASSET_PATHS[id] ?: return emptySet()
        val domains = runCatching {
            context.assets.open(path).bufferedReader().use { it.readLines() }
        }.getOrDefault(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
        cache[id] = domains
        return domains
    }
}
