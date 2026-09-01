package com.habitsfirst.androidclone.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Central source of "what day is it". A thin wrapper so tests (and the daily-reset
 * worker) can reason about dates as plain ISO strings, matching how they're stored
 * in [com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity].
 */
object DateProvider {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayString(): String = LocalDate.now().format(formatter)

    fun tomorrowString(): String = LocalDate.now().plusDays(1).format(formatter)

    fun toDateString(date: LocalDate): String = date.format(formatter)

    fun fromDateString(date: String): LocalDate = LocalDate.parse(date, formatter)

    fun isToday(date: String): Boolean = date == todayString()

    /**
     * Emits today's date string immediately, then again every time the calendar date
     * actually changes. Use this (via `flatMapLatest`) instead of capturing
     * [todayString] once as a plain `val` inside a long-lived `Flow` builder -- a
     * `combine`/`map` chain built that way keeps querying the date it happened to be
     * built on forever, so a screen left open (or a ViewModel kept alive) across
     * midnight silently stops reflecting today's actual data.
     */
    fun currentDateFlow(): Flow<String> = flow {
        while (true) {
            emit(todayString())
            delay(CURRENT_DATE_POLL_INTERVAL_MILLIS)
        }
    }.distinctUntilChanged()

    private const val CURRENT_DATE_POLL_INTERVAL_MILLIS = 60_000L
}
