package com.locke.app.data.repository

import com.locke.app.data.local.dao.StreakScarDao
import com.locke.app.data.local.entity.StreakScarEntity
import com.locke.app.domain.model.Habit
import com.locke.app.domain.model.HabitKind
import com.locke.app.domain.model.HabitType
import com.locke.app.util.DateProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic consequence engine: everything in this app that should be able to "punish"
 * the user (an antihabit slip today, a missed bedtime tomorrow) goes through one of
 * these three primitives instead of inventing its own bespoke mechanic.
 */
@Singleton
class PenaltyRepository @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
    private val streakScarDao: StreakScarDao,
) {
    suspend fun isPenaltyLockActive(): Boolean =
        preferencesRepository.penaltyLockedUntilEpochMillis.first() > System.currentTimeMillis()

    /** Keeps blocked apps locked for [minutes] more, even once habits are complete. */
    suspend fun extendBlock(minutes: Int, reason: String) {
        preferencesRepository.extendPenaltyLock(System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Adds a one-day, auto-expiring gating habit -- "do this too, today only". */
    suspend fun addMakeupHabit(reason: String) {
        val today = DateProvider.todayString()
        habitRepository.saveHabit(
            Habit(
                name = "Makeup: $reason",
                type = HabitType.TALLY,
                targetValue = 1,
                kind = HabitKind.GATING,
                expiresAfterDate = today,
            ),
        )
    }

    /** Marks [date] as a failed day regardless of what actually got completed -- breaks the streak. */
    suspend fun markStreakBroken(reason: String, date: String = DateProvider.todayString()) {
        streakScarDao.insert(StreakScarEntity(date = date, reason = reason))
    }

    /** Every antihabit slip has an immediate, felt consequence: apps stay locked a bit longer. */
    suspend fun applyAntihabitSlipPenalty(habitName: String) {
        extendBlock(ANTIHABIT_SLIP_PENALTY_MINUTES, reason = "antihabit slip: $habitName")
    }

    companion object {
        const val ANTIHABIT_SLIP_PENALTY_MINUTES = 10
    }
}
