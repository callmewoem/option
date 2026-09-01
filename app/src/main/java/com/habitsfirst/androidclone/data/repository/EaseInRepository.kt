package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.domain.model.HabitKind
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the onboarding "ease into it" ramp: habits chosen together at onboarding
 * (see [com.habitsfirst.androidclone.domain.model.Habit.easeInOrder]) start with only
 * the easiest one GATING and the rest TRACKED. Once the current frontier habit has been
 * completed for [PreferencesRepository.easeInStreakLength] consecutive days, the next
 * one in line is promoted -- gates accumulate, they don't swap out.
 */
@Singleton
class EaseInRepository @Inject constructor(
    private val habitRepository: HabitRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    /** Safe to call often (e.g. every worker tick) -- a no-op on every day but a graduation day. */
    suspend fun maybeGraduateNextHabit() {
        val easeInHabits = habitRepository.getEaseInHabitsOnce()
        val activeGate = easeInHabits.filter { it.kind == HabitKind.GATING }.maxByOrNull { it.easeInOrder!! } ?: return
        val next = easeInHabits.getOrNull(easeInHabits.indexOf(activeGate) + 1) ?: return

        val requiredStreak = preferencesRepository.easeInStreakLength.first()
        val streak = habitRepository.computeHabitStreak(activeGate.id, requiredStreak)
        if (streak >= requiredStreak) {
            habitRepository.promoteHabitToGating(next.id)
        }
    }
}
