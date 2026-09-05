package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.repository.HabitRepository.Companion.completionTimeDistributionOf
import com.habitsfirst.androidclone.data.repository.HabitRepository.Companion.consistencyStatsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Covers the pure math behind [HabitRepository.getConsistencyStats] and
 * [HabitRepository.getCompletionTimeDistribution] -- both are plain functions of
 * already-fetched data, so they're testable without a Room DB or Android runtime.
 */
class HabitRepositoryStatsTest {

    @Test
    fun `consistencyStatsOf is zero stddev for a perfectly steady window`() {
        val stats = consistencyStatsOf(listOf(0.5f, 0.5f, 0.5f, 0.5f))
        assertEquals(0.5f, stats.meanFraction, 0.001f)
        assertEquals(0f, stats.standardDeviation, 0.001f)
        assertEquals(4, stats.daysCounted)
    }

    @Test
    fun `consistencyStatsOf has high stddev for an on-again off-again window`() {
        val steady = consistencyStatsOf(listOf(0.5f, 0.5f, 0.5f, 0.5f))
        val erratic = consistencyStatsOf(listOf(1f, 0f, 1f, 0f))
        // Same mean (0.5), but the erratic window should read as far less consistent.
        assertEquals(steady.meanFraction, erratic.meanFraction, 0.001f)
        assertTrue(erratic.standardDeviation > steady.standardDeviation)
        assertEquals(0.5f, erratic.standardDeviation, 0.001f)
    }

    @Test
    fun `consistencyStatsOf handles an empty window without dividing by zero`() {
        val stats = consistencyStatsOf(emptyList())
        assertEquals(0f, stats.meanFraction, 0.001f)
        assertEquals(0f, stats.standardDeviation, 0.001f)
        assertEquals(0, stats.daysCounted)
    }

    @Test
    fun `completionTimeDistributionOf buckets a late-night-heavy pattern into NIGHT`() {
        val zone = ZoneOffset.UTC
        val timestamps = listOf(
            LocalDate.of(2026, 1, 1).atTime(LocalTime.of(23, 30)).atZone(zone).toInstant().toEpochMilli(),
            LocalDate.of(2026, 1, 2).atTime(LocalTime.of(23, 45)).atZone(zone).toInstant().toEpochMilli(),
        )
        // Force the system default zone to UTC for the duration of this assertion so the
        // bucketing (which reads ZoneId.systemDefault()) lines up with the UTC timestamps
        // built above, regardless of what zone the test JVM happens to run in.
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneId.of("UTC")))
            val distribution = completionTimeDistributionOf(timestamps)
            assertEquals(2, distribution.totalCount)
            assertEquals(2, distribution.bucketCounts[TimeOfDayBucket.NIGHT])
            assertTrue(distribution.averageMinutesSinceMidnight!! > 22 * 60)
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }

    @Test
    fun `completionTimeDistributionOf is null average with no timestamps`() {
        val distribution = completionTimeDistributionOf(emptyList())
        assertEquals(0, distribution.totalCount)
        assertNull(distribution.averageMinutesSinceMidnight)
    }
}
