package com.habitsfirst.androidclone.service

import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Covers [WeeklyDigestWorker.isDue] -- the pure "is this the right day/time and haven't
 * sent this week yet" gate, extracted out of [WeeklyDigestWorker.doWork] specifically so
 * it can be exercised without DataStore or WorkManager.
 */
class WeeklyDigestWorkerTest {

    private val sunday = LocalDate.of(2026, 9, 6) // a Sunday
    private val monday = sunday.plusDays(1)

    private fun settings(
        enabled: Boolean = true,
        dayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
        time: String = "18:00",
    ) = PreferencesRepository.WeeklyDigestSettings(enabled, dayOfWeek, time)

    @Test
    fun `not due when disabled even on the right day and time`() {
        assertFalse(
            WeeklyDigestWorker.isDue(
                settings(enabled = false),
                lastSentDate = null,
                today = sunday,
                now = LocalTime.of(18, 5),
            ),
        )
    }

    @Test
    fun `not due on a day other than the configured day of week`() {
        assertFalse(
            WeeklyDigestWorker.isDue(
                settings(dayOfWeek = DayOfWeek.SUNDAY),
                lastSentDate = null,
                today = monday,
                now = LocalTime.of(18, 5),
            ),
        )
    }

    @Test
    fun `not due before the configured time`() {
        assertFalse(
            WeeklyDigestWorker.isDue(
                settings(time = "18:00"),
                lastSentDate = null,
                today = sunday,
                now = LocalTime.of(17, 59),
            ),
        )
    }

    @Test
    fun `not due once well past the configured time's window`() {
        assertFalse(
            WeeklyDigestWorker.isDue(
                settings(time = "18:00"),
                lastSentDate = null,
                today = sunday,
                now = LocalTime.of(19, 0),
            ),
        )
    }

    @Test
    fun `due right at the configured time on the right day, never sent before`() {
        assertTrue(
            WeeklyDigestWorker.isDue(
                settings(time = "18:00"),
                lastSentDate = null,
                today = sunday,
                now = LocalTime.of(18, 0),
            ),
        )
    }

    @Test
    fun `due within the window after the configured time`() {
        assertTrue(
            WeeklyDigestWorker.isDue(
                settings(time = "18:00"),
                lastSentDate = null,
                today = sunday,
                now = LocalTime.of(18, 15),
            ),
        )
    }

    @Test
    fun `not due when already sent today`() {
        assertFalse(
            WeeklyDigestWorker.isDue(
                settings(time = "18:00"),
                lastSentDate = "2026-09-06",
                today = sunday,
                now = LocalTime.of(18, 5),
            ),
        )
    }

    @Test
    fun `due again the following week even though a digest was sent 7 days ago`() {
        val nextSunday = sunday.plusWeeks(1)
        assertTrue(
            WeeklyDigestWorker.isDue(
                settings(time = "18:00"),
                lastSentDate = "2026-09-06",
                today = nextSunday,
                now = LocalTime.of(18, 5),
            ),
        )
    }
}
