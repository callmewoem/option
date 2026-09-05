package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.AccountabilityBuddyDao
import com.habitsfirst.androidclone.data.local.dao.PendingStatsSyncDao
import com.habitsfirst.androidclone.data.local.entity.PendingStatsSyncEntity
import com.habitsfirst.androidclone.data.local.entity.toDomain
import com.habitsfirst.androidclone.data.local.entity.toEntity
import com.habitsfirst.androidclone.data.remote.AccountabilityApiClient
import com.habitsfirst.androidclone.data.remote.AccountabilityApiException
import com.habitsfirst.androidclone.domain.model.AccountabilityBuddy
import com.habitsfirst.androidclone.domain.model.DailySummary
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.PairingCode
import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single seam UI goes through for accountability-buddy features -- nothing should
 * call [AccountabilityApiClient] directly. Buddies and their last-synced summaries are
 * cached in Room (see `data/local/entity/AccountabilityBuddyEntity.kt`), so the buddy
 * list still renders -- and a stats share still queues instead of getting lost -- with
 * no connectivity or no backend configured at all, which is the common case today since
 * no default backend is hosted anywhere. Mirrors the "seam behind a stub-swappable
 * interface" shape of [com.habitsfirst.androidclone.data.billing.EntitlementRepository].
 */
@Singleton
class AccountabilityRepository @Inject constructor(
    private val apiClient: AccountabilityApiClient,
    private val buddyDao: AccountabilityBuddyDao,
    private val pendingSyncDao: PendingStatsSyncDao,
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    /** The cached buddy list -- newest state Room has, refreshed opportunistically by [refreshBuddies]. */
    val buddies: Flow<List<AccountabilityBuddy>> = buddyDao.observeAll().map { list -> list.map { it.toDomain() } }

    val shareStatsEnabled: Flow<Boolean> = preferencesRepository.shareDailyStatsEnabled

    suspend fun setShareStatsEnabled(enabled: Boolean) = preferencesRepository.setShareDailyStatsEnabled(enabled)

    val myPairingCode: Flow<String?> = preferencesRepository.myPairingCode

    /**
     * Asks the backend for a fresh pairing code and stores it for display in Settings.
     * Returns null (leaving any previously stored code untouched) on any backend
     * failure -- unset base URL, unreachable host, non-2xx -- so a broken backend
     * never crashes Settings or blanks out a code that was working before.
     */
    suspend fun regeneratePairingCode(): PairingCode? = try {
        val code = apiClient.createPairingCode()
        preferencesRepository.setMyPairingCode(code.code)
        code
    } catch (e: AccountabilityApiException) {
        null
    }

    /**
     * Redeems [code] with the backend and caches the returned buddy. Returns false (no
     * buddy added) on any backend failure -- the common case today, since no backend
     * exists yet -- without crashing or leaving a half-written row.
     */
    suspend fun addBuddy(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.isBlank()) return false
        return try {
            val buddy = apiClient.addBuddy(trimmed)
            buddyDao.upsert(buddy.toEntity())
            true
        } catch (e: AccountabilityApiException) {
            false
        }
    }

    /**
     * Re-fetches every buddy's latest summary from the backend and overwrites the
     * cache -- including dropping any cached buddy the backend no longer lists (e.g.
     * unpaired from the other side). Silently leaves the existing cache untouched on
     * any failure -- the buddy list still renders whatever it last knew rather than
     * going blank.
     */
    suspend fun refreshBuddies() {
        try {
            val fetched = apiClient.fetchBuddySummaries()
            val fetchedIds = fetched.map { it.id }.toSet()
            buddyDao.getAllOnce().forEach { cached ->
                if (cached.id !in fetchedIds) buddyDao.delete(cached.id)
            }
            buddyDao.upsertAll(fetched.map { it.toEntity() })
        } catch (e: AccountabilityApiException) {
            // No backend configured, unreachable, or a bad response -- nothing better
            // to show than what's already cached.
        }
    }

    /**
     * Builds today's outbound summary purely from data [HabitRepository] already
     * tracks -- today's GATING progress and the current streak -- deliberately
     * independent of any other in-flight unit's new stats fields, which may not exist
     * yet when this lands.
     */
    private suspend fun buildTodaySummary(): DailySummary {
        val todayProgress = habitRepository.observeTodayProgressByKind(HabitKind.GATING).first()
        return DailySummary(
            date = DateProvider.todayString(),
            habitsCompleted = todayProgress.count { it.isCompleted },
            totalHabits = todayProgress.size,
            currentStreak = habitRepository.computeCurrentStreak(),
        )
    }

    /**
     * Pushes today's summary if sharing is turned on; a no-op otherwise. On any push
     * failure (no backend configured, unreachable, non-2xx) the summary is queued in
     * the local outbox instead of lost -- [retryPendingSyncs] picks it back up later.
     */
    suspend fun shareTodayStatsIfEnabled() {
        if (!preferencesRepository.shareDailyStatsEnabled.first()) return
        val summary = buildTodaySummary()
        val result = apiClient.pushDailySummary(summary)
        if (result.isSuccess) {
            pendingSyncDao.delete(summary.date)
        } else {
            pendingSyncDao.upsert(
                PendingStatsSyncEntity(
                    date = summary.date,
                    habitsCompleted = summary.habitsCompleted,
                    totalHabits = summary.totalHabits,
                    currentStreak = summary.currentStreak,
                ),
            )
        }
    }

    /**
     * Retries every queued summary once. A "try now, leave it queued on failure" pass
     * -- no backoff/scheduling here, just called opportunistically (app foreground, or
     * before the next [shareTodayStatsIfEnabled]).
     */
    suspend fun retryPendingSyncs() {
        pendingSyncDao.getAllOnce().forEach { pending ->
            val summary = DailySummary(
                date = pending.date,
                habitsCompleted = pending.habitsCompleted,
                totalHabits = pending.totalHabits,
                currentStreak = pending.currentStreak,
            )
            val result = apiClient.pushDailySummary(summary)
            if (result.isSuccess) {
                pendingSyncDao.delete(pending.date)
            } else {
                pendingSyncDao.upsert(
                    pending.copy(
                        attemptCount = pending.attemptCount + 1,
                        lastAttemptAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /** True if any summary is still waiting to be pushed -- e.g. for a "pending" indicator in Settings. */
    suspend fun hasPendingSyncs(): Boolean = pendingSyncDao.count() > 0

    /**
     * Opportunistic "sync now": re-fetches buddies and retries anything still queued.
     * There's no periodic worker for this pass (see class doc) -- callers trigger it
     * themselves, e.g. [com.habitsfirst.androidclone.ui.settings.SettingsViewModel] on
     * init and whenever the Settings screen resumes.
     */
    suspend fun syncNow() {
        refreshBuddies()
        retryPendingSyncs()
    }
}
