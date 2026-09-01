package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.domain.model.LootboxReward
import com.habitsfirst.androidclone.domain.model.ThemeVariant
import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Awards one lootbox the first time all of today's gating habits get completed, and
 * manages the tokens/unlocks it can contain. Weighted so a grace-period token is
 * common, a theme unlock and the cosmetic gold star are uncommon, and a task-skip
 * token -- the strongest reward, since it erases a whole habit for the day -- is rare.
 */
@Singleton
class LootboxRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) {
    val graceTokenCount: Flow<Int> = preferencesRepository.graceTokenCount
    val taskSkipTokenCount: Flow<Int> = preferencesRepository.taskSkipTokenCount

    /** Returns the reward won, or null if today's lootbox was already claimed (or habits aren't done). */
    suspend fun maybeAwardDailyLootbox(
        allGatingHabitsComplete: Boolean,
        date: String = DateProvider.todayString(),
    ): LootboxReward? {
        if (!allGatingHabitsComplete) return null
        if (preferencesRepository.lastLootboxAwardedDate.first() == date) return null
        preferencesRepository.setLastLootboxAwardedDate(date)
        val reward = rollReward()
        grant(reward)
        return reward
    }

    private suspend fun rollReward(): LootboxReward {
        val unlocked = preferencesRepository.unlockedThemeVariantIds.first()
        val lockedVariants = ThemeVariant.entries.filter { it.name !in unlocked }

        val pool = buildList {
            repeat(WEIGHT_GRACE_PERIOD) { add(LootboxReward.GracePeriod) }
            repeat(WEIGHT_TASK_SKIP) { add(LootboxReward.TaskSkip) }
            repeat(WEIGHT_GOLD_STAR) { add(LootboxReward.GoldStar) }
            if (lockedVariants.isNotEmpty()) {
                repeat(WEIGHT_THEME_UNLOCK) { add(LootboxReward.ThemeUnlock(lockedVariants.random())) }
            } else {
                // Every variant is already unlocked -- fold that weight back into the common reward.
                repeat(WEIGHT_THEME_UNLOCK) { add(LootboxReward.GracePeriod) }
            }
        }
        return pool.random()
    }

    private suspend fun grant(reward: LootboxReward) {
        when (reward) {
            LootboxReward.GracePeriod -> preferencesRepository.addGraceTokens(1)
            LootboxReward.TaskSkip -> preferencesRepository.addTaskSkipTokens(1)
            is LootboxReward.ThemeUnlock -> preferencesRepository.unlockThemeVariant(reward.variant.name)
            LootboxReward.GoldStar -> preferencesRepository.addGoldStarDate(DateProvider.todayString())
        }
    }

    /** Consumes a grace token and unlocks blocked apps (except during bedtime) for [minutes]. */
    suspend fun redeemGraceToken(minutes: Int = 1): Boolean {
        if (!preferencesRepository.consumeGraceToken()) return false
        preferencesRepository.setGraceUnlockUntil(System.currentTimeMillis() + minutes * 60_000L)
        return true
    }

    suspend fun consumeTaskSkipToken(): Boolean = preferencesRepository.consumeTaskSkipToken()

    suspend fun isGraceUnlockActive(): Boolean =
        preferencesRepository.graceUnlockUntilEpochMillis.first() > System.currentTimeMillis()

    companion object {
        private const val WEIGHT_GRACE_PERIOD = 50
        private const val WEIGHT_TASK_SKIP = 10
        private const val WEIGHT_GOLD_STAR = 20
        private const val WEIGHT_THEME_UNLOCK = 20
    }
}
