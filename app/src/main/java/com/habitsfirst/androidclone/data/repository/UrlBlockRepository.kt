package com.habitsfirst.androidclone.data.repository

import android.content.Context
import com.habitsfirst.androidclone.data.local.dao.BlockedDomainDao
import com.habitsfirst.androidclone.data.local.dao.BlockListDao
import com.habitsfirst.androidclone.data.local.entity.BlockedDomainEntity
import com.habitsfirst.androidclone.data.local.entity.BlockListEntity
import com.habitsfirst.androidclone.data.local.entity.toDomain
import com.habitsfirst.androidclone.domain.model.BlockListSource
import com.habitsfirst.androidclone.domain.model.BlockMode
import com.habitsfirst.androidclone.domain.model.UrlBlockList
import com.habitsfirst.androidclone.util.PremadeBlocklists
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One matched domain's rule, keyed by host for [AppBlockAccessibilityService][com.habitsfirst.androidclone.service.AppBlockAccessibilityService]'s per-navigation lookup. */
data class ActiveDomainBlock(val listName: String, val blockMode: BlockMode)

/**
 * URL blocking: the two premade "blanket" lists (porn, social media) plus any custom
 * lists the user builds, each independently enabled and set to [BlockMode.GATED] or
 * [BlockMode.PERMANENT].
 */
@Singleton
class UrlBlockRepository @Inject constructor(
    private val blockListDao: BlockListDao,
    private val blockedDomainDao: BlockedDomainDao,
    @ApplicationContext private val appContext: Context,
) {
    // A Singleton-scoped, fire-and-forget seed of the two premade rows, run once at
    // first injection -- mirrors AppBlockAccessibilityService's own long-lived scope.
    // insertIfAbsent is idempotent, so this is safe even if it somehow ran twice.
    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        seedScope.launch { ensurePremadeListsSeeded() }
    }

    private suspend fun ensurePremadeListsSeeded() {
        PremadeBlocklists.ids.forEach { id ->
            blockListDao.insertIfAbsent(
                BlockListEntity(
                    id = id,
                    name = PremadeBlocklists.nameFor(id),
                    source = PremadeBlocklists.sourceFor(id),
                    // Off by default, and PERMANENT once turned on -- "blanket" lists
                    // are meant to be a hard block, not something gating quietly lifts.
                    blockMode = BlockMode.PERMANENT,
                    isEnabled = false,
                ),
            )
        }
    }

    /** Every block list (premade + custom) with its live domain count, for the management screen. */
    fun observeBlockLists(): Flow<List<UrlBlockList>> = combine(
        blockListDao.observeAll(),
        blockedDomainDao.observeAll(),
    ) { lists, domains ->
        val customCounts = domains.groupingBy { it.listId }.eachCount()
        lists.map { entity ->
            val count = if (entity.source == BlockListSource.CUSTOM) {
                customCounts[entity.id] ?: 0
            } else {
                PremadeBlocklists.domainsFor(appContext, entity.id).size
            }
            entity.toDomain(count)
        }.sortedWith(compareBy({ it.source == BlockListSource.CUSTOM }, { it.name.lowercase() }))
    }

    fun observeCustomListDomains(listId: String): Flow<List<String>> =
        blockedDomainDao.observeDomainsForList(listId)

    /**
     * The accessibility service's live index: every domain currently covered by an
     * *enabled* list, mapped to the rule covering it. A domain enabled under both a
     * gated and a permanent list resolves to permanent -- the stricter rule wins.
     */
    fun observeActiveDomainIndex(): Flow<Map<String, ActiveDomainBlock>> = combine(
        blockListDao.observeAll(),
        blockedDomainDao.observeAll(),
    ) { lists, domains ->
        val customDomainsByList = domains.groupBy({ it.listId }, { it.domain })
        val index = mutableMapOf<String, ActiveDomainBlock>()
        val enabled = lists.filter { it.isEnabled }
        enabled.filter { it.blockMode == BlockMode.GATED }.forEach { indexList(index, it, customDomainsByList) }
        enabled.filter { it.blockMode == BlockMode.PERMANENT }.forEach { indexList(index, it, customDomainsByList) }
        index
    }

    private fun indexList(
        index: MutableMap<String, ActiveDomainBlock>,
        list: BlockListEntity,
        customDomainsByList: Map<String, List<String>>,
    ) {
        val domains = if (list.source == BlockListSource.CUSTOM) {
            customDomainsByList[list.id].orEmpty()
        } else {
            PremadeBlocklists.domainsFor(appContext, list.id)
        }
        domains.forEach { domain -> index[domain] = ActiveDomainBlock(list.name, list.blockMode) }
    }

    suspend fun setListEnabled(listId: String, enabled: Boolean) {
        val list = blockListDao.getById(listId) ?: return
        blockListDao.upsert(list.copy(isEnabled = enabled))
    }

    suspend fun setListBlockMode(listId: String, mode: BlockMode) {
        val list = blockListDao.getById(listId) ?: return
        blockListDao.upsert(list.copy(blockMode = mode))
    }

    /** Returns the new list's id. Starts enabled and gated -- the user opts a custom list into PERMANENT explicitly. */
    suspend fun createCustomList(name: String): String {
        val id = UUID.randomUUID().toString()
        blockListDao.upsert(
            BlockListEntity(
                id = id,
                name = name.trim().ifBlank { "Custom list" },
                source = BlockListSource.CUSTOM,
                blockMode = BlockMode.GATED,
                isEnabled = true,
            ),
        )
        return id
    }

    suspend fun renameCustomList(listId: String, name: String) {
        val list = blockListDao.getById(listId) ?: return
        if (list.source != BlockListSource.CUSTOM) return
        blockListDao.upsert(list.copy(name = name.trim().ifBlank { list.name }))
    }

    /** No-op for a premade list -- only a custom list can be deleted outright. */
    suspend fun deleteCustomList(listId: String) {
        val list = blockListDao.getById(listId) ?: return
        if (list.source != BlockListSource.CUSTOM) return
        blockListDao.deleteById(listId) // cascades to blocked_domains
    }

    suspend fun addDomain(listId: String, rawDomain: String) {
        val domain = normalizeDomain(rawDomain) ?: return
        blockedDomainDao.insert(BlockedDomainEntity(listId, domain))
    }

    suspend fun removeDomain(listId: String, domain: String) {
        blockedDomainDao.delete(listId, domain)
    }

    companion object {
        /** Strips a scheme/path/port off a pasted URL down to a bare registrable-ish host, lowercased. */
        fun normalizeDomain(raw: String): String? {
            var value = raw.trim().lowercase()
            if (value.isEmpty()) return null
            value = value.removePrefix("https://").removePrefix("http://")
            value = value.substringBefore('/').substringBefore('?').substringBefore(':')
            value = value.removePrefix("www.")
            return value.takeIf { it.contains('.') && it.none { c -> c.isWhitespace() } }
        }

        /**
         * Walks [host] up from most to least specific ("m.pornhub.com" ->
         * "pornhub.com" -> stop) looking for an entry in [index], so a list entry for
         * a bare domain also covers its subdomains.
         */
        fun findBlockForHost(host: String, index: Map<String, ActiveDomainBlock>): ActiveDomainBlock? {
            var candidate = host
            while (true) {
                index[candidate]?.let { return it }
                val dot = candidate.indexOf('.')
                if (dot < 0) return null
                val next = candidate.substring(dot + 1)
                if (!next.contains('.')) return null // don't walk all the way down to a bare TLD
                candidate = next
            }
        }
    }
}
