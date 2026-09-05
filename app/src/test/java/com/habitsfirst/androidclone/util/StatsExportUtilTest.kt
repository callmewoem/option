package com.habitsfirst.androidclone.util

import com.habitsfirst.androidclone.data.repository.HabitCompletionStat
import com.habitsfirst.androidclone.domain.model.Habit
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.domain.model.Todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StatsExportUtil.buildCsv] and [StatsExportUtil.buildJson] are plain functions of
 * already-fetched data, so this exercises them directly without touching Room or a
 * Context -- see [StatsExportUtil]'s KDoc for why that split exists.
 */
class StatsExportUtilTest {

    @Test
    fun `buildCsv writes one row per day with a header`() {
        val rows = listOf(
            DayExportRow("2026-01-01", 1f, isPerfectDay = true, streakBroken = false, streakBreakReason = null),
            DayExportRow("2026-01-02", 0.5f, isPerfectDay = false, streakBroken = false, streakBreakReason = null),
            DayExportRow("2026-01-03", 0f, isPerfectDay = false, streakBroken = true, streakBreakReason = "Missed everything"),
        )

        val csv = StatsExportUtil.buildCsv(rows)
        val lines = csv.trim().lines()

        assertEquals(4, lines.size)
        assertEquals("date,completion_fraction,is_perfect_day,streak_broken,streak_break_reason", lines[0])
        assertEquals("2026-01-01,1.000,true,false,", lines[1])
        assertEquals("2026-01-02,0.500,false,false,", lines[2])
        assertEquals("2026-01-03,0.000,false,true,Missed everything", lines[3])
    }

    @Test
    fun `buildCsv quotes a reason containing a comma`() {
        val rows = listOf(
            DayExportRow("2026-01-01", 0f, isPerfectDay = false, streakBroken = true, streakBreakReason = "Skipped, then slipped"),
        )

        val csv = StatsExportUtil.buildCsv(rows)

        assertTrue(csv.contains("\"Skipped, then slipped\""))
    }

    @Test
    fun `buildCsv on an empty range is just the header`() {
        val csv = StatsExportUtil.buildCsv(emptyList())

        assertEquals("date,completion_fraction,is_perfect_day,streak_broken,streak_break_reason\n", csv)
    }

    @Test
    fun `buildJson includes day scores, habit stats, and todos`() {
        val data = StatsExportData(
            startDate = "2026-01-01",
            endDate = "2026-01-02",
            dayRows = listOf(
                DayExportRow("2026-01-01", 1f, isPerfectDay = true, streakBroken = false, streakBreakReason = null),
                DayExportRow("2026-01-02", 0f, isPerfectDay = false, streakBroken = true, streakBreakReason = "Broke it"),
            ),
            habitStats = listOf(
                HabitCompletionStat(
                    habit = Habit(name = "Read", type = HabitType.TALLY, targetValue = 1),
                    rate = 0.75f,
                    completedCount = 3,
                    totalDays = 4,
                ),
            ),
            todos = listOf(
                Todo(title = "Buy milk", date = "2026-01-01", isDone = true, createdAtEpochMillis = 1_700_000_000_000L),
            ),
        )

        val json = StatsExportUtil.buildJson(data)

        assertTrue(json.contains("\"startDate\": \"2026-01-01\""))
        assertTrue(json.contains("\"date\": \"2026-01-02\""))
        assertTrue(json.contains("\"streakBreakReason\": \"Broke it\""))
        assertTrue(json.contains("\"name\": \"Read\""))
        assertTrue(json.contains("\"completionRate\": 0.75"))
        assertTrue(json.contains("\"title\": \"Buy milk\""))
        assertTrue(json.contains("\"isDone\": true"))
    }

    @Test
    fun `buildJson escapes quotes and newlines in free text`() {
        val data = StatsExportData(
            startDate = "2026-01-01",
            endDate = "2026-01-01",
            dayRows = emptyList(),
            habitStats = emptyList(),
            todos = listOf(Todo(title = "Say \"hi\"\nto Bob", date = "2026-01-01")),
        )

        val json = StatsExportUtil.buildJson(data)

        assertTrue(json.contains("Say \\\"hi\\\"\\nto Bob"))
    }
}
