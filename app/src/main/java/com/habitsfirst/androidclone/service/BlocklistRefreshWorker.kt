package com.habitsfirst.androidclone.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.util.PremadeBlocklistFetcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically re-syncs the two premade "blanket" URL blocklists (porn, social media)
 * from their upstream source, so users get newly added domains without an app update.
 * Runs whether or not either list is currently enabled -- cheap, and keeps the cache
 * warm for whenever the user turns one on. See [PremadeBlocklistFetcher] for the actual
 * fetch/parse/write; a failed or offline run just leaves the existing cached (or bundled
 * seed) copy in place; WorkManager's NetworkType.CONNECTED constraint means this mostly
 * doesn't even get to run while offline.
 */
@HiltWorker
class BlocklistRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val fetcher: PremadeBlocklistFetcher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        fetcher.refreshAll()
        // Best-effort either way: a partial or total failure just means next time still
        // has the old cached/bundled copy, not a stuck list -- retrying wouldn't help
        // more than the next periodic run already will.
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "blocklist_refresh_periodic"
    }
}
