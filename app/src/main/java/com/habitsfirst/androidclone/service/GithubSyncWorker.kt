package com.habitsfirst.androidclone.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.verification.GitHubApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically checks every active
 * [com.habitsfirst.androidclone.domain.model.HabitType.GITHUB_CONTRIBUTION] habit's
 * saved username for today's activity, marking it done once found -- same
 * "read once per tick, only ever mark done, never undo" shape as [LocationSyncWorker]:
 * a rate-limited or failed check this tick shouldn't un-complete a habit an earlier
 * tick (or a manual tap) already confirmed.
 */
@HiltWorker
class GithubSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val gitHubApiClient: GitHubApiClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val habits = habitRepository.getGithubHabitsOnce()
        for (habit in habits) {
            val username = habit.targetGithubUsername ?: continue
            if (gitHubApiClient.hasContributedToday(username)) {
                habitRepository.markDoneIfDetected(habit.id)
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "github_sync_periodic"
        const val ONE_OFF_NAME = "github_sync_one_off"
    }
}
