package com.habitsfirst.androidclone.util

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

    fun toDateString(date: LocalDate): String = date.format(formatter)

    fun fromDateString(date: String): LocalDate = LocalDate.parse(date, formatter)

    fun isToday(date: String): Boolean = date == todayString()
}
