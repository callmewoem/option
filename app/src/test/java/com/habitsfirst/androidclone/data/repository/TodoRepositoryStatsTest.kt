package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.TodoCompletionTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers [TodoRepository.averageCompletionMinutes]'s pure math -- see [HabitRepositoryStatsTest] for the same rationale. */
class TodoRepositoryStatsTest {

    @Test
    fun `averageCompletionMinutes is null with no completed todos`() {
        assertNull(TodoRepository.averageCompletionMinutes(emptyList()))
    }

    @Test
    fun `averageCompletionMinutes averages elapsed time across todos`() {
        val timings = listOf(
            TodoCompletionTiming(createdAtEpochMillis = 0L, completedAtEpochMillis = 10 * 60_000L), // 10 min
            TodoCompletionTiming(createdAtEpochMillis = 0L, completedAtEpochMillis = 30 * 60_000L), // 30 min
        )
        val average = TodoRepository.averageCompletionMinutes(timings)
        assertEquals(20f, average!!, 0.01f)
    }

    @Test
    fun `averageCompletionMinutes clamps a bad completedAt-before-createdAt row to zero rather than going negative`() {
        val timings = listOf(TodoCompletionTiming(createdAtEpochMillis = 10_000L, completedAtEpochMillis = 5_000L))
        val average = TodoRepository.averageCompletionMinutes(timings)
        assertEquals(0f, average!!, 0.01f)
    }
}
