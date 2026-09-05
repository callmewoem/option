package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.BlockAttemptDao
import com.habitsfirst.androidclone.data.local.entity.BlockAttemptEntity
import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Impulse-control tracking: every time [com.habitsfirst.androidclone.service.AppBlockAccessibilityService]
 * actually covers a blocked app/URL with the block screen, that's one "attempt" -- the
 * user tried to open something locked. Distinct from the block-list config repositories
 * ([BlockedAppRepository], [UrlBlockRepository]); this one is a pure event log, useful
 * for self-review (e.g. "how often am I reaching for a locked app without thinking").
 */
@Singleton
class BlockAttemptRepository @Inject constructor(
    private val blockAttemptDao: BlockAttemptDao,
) {
    /** Logs one blocked-open attempt against [target] (a package name or URL host), dated today. */
    suspend fun logAttempt(target: String, date: String = DateProvider.todayString()) {
        blockAttemptDao.insert(BlockAttemptEntity(target = target, date = date))
    }

    suspend fun getAttemptCountForDate(date: String = DateProvider.todayString()): Int =
        blockAttemptDao.getCountForDate(date)

    /** Live version of [getAttemptCountForDate] -- for a UI element (the Home chip) that should update the instant a new attempt is logged. */
    fun observeAttemptCountForDate(date: String): Flow<Int> = blockAttemptDao.observeCountForDate(date)

    suspend fun getAttemptCountInRange(startDate: String, endDate: String): Int =
        blockAttemptDao.getCountInRange(startDate, endDate)

    /** Attempt count per date within the range, for the stats screen's distribution chart. Dates with zero attempts are simply absent. */
    suspend fun getDailyAttemptCountsInRange(startDate: String, endDate: String): Map<String, Int> =
        blockAttemptDao.getDailyCountsInRange(startDate, endDate).associate { it.date to it.count }
}
