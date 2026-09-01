package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.TodoDao
import com.habitsfirst.androidclone.data.local.dao.TodoRow
import com.habitsfirst.androidclone.data.local.entity.TodoEntity
import com.habitsfirst.androidclone.data.local.entity.toDayOfWeekSet
import com.habitsfirst.androidclone.data.local.entity.toEntity
import com.habitsfirst.androidclone.data.local.entity.toRepeatDaysMask
import com.habitsfirst.androidclone.domain.model.Todo
import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
) {
    fun observeForDate(date: String): Flow<List<Todo>> {
        val dayBit = 1 shl (DateProvider.fromDateString(date).dayOfWeek.value - 1)
        return todoDao.observeForDate(date, dayBit).map { rows -> rows.map { it.toDomain() } }
    }

    /**
     * Today's todos, re-derived as the calendar date actually changes -- unlike
     * `observeForDate(DateProvider.todayString())`, which (as a Flow held for a whole
     * ViewModel's lifetime) would otherwise keep watching whatever date it happened to
     * be built on. See [DateProvider.currentDateFlow].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeForToday(): Flow<List<Todo>> =
        DateProvider.currentDateFlow().flatMapLatest { date -> observeForDate(date) }

    /**
     * Adds a task. A blank [repeatDays] adds a one-off task due [date]; a non-empty one
     * adds a task that recurs on those days of the week starting [date] (e.g. "hoover"
     * every Sunday, or a weekly "note recap").
     */
    suspend fun addTodo(title: String, date: String = DateProvider.todayString(), repeatDays: Set<DayOfWeek> = emptySet()) {
        if (title.isBlank()) return
        todoDao.upsert(
            TodoEntity(title = title.trim(), date = date, repeatDaysMask = repeatDays.toRepeatDaysMask()),
        )
    }

    /** [todo] as loaded for [date] (its occurrence date if recurring, its due date otherwise). */
    suspend fun setDone(todo: Todo, done: Boolean, date: String = DateProvider.todayString()) {
        if (todo.isRecurring) {
            todoDao.setRecurringOccurrenceDone(todo.id, date, done)
        } else {
            todoDao.setDone(todo.id, done)
        }
    }

    /** Deletes [todo] -- for a recurring task, this removes the whole series, not just today's occurrence. */
    suspend fun delete(todo: Todo) {
        todoDao.deleteOccurrencesFor(todo.id)
        todoDao.delete(todo.toEntity())
    }

    suspend fun hasTodosForDate(date: String = DateProvider.todayString()): Boolean =
        todoDao.getCountForDate(date) > 0
}

private fun TodoRow.toDomain(): Todo = Todo(
    id = id,
    title = title,
    date = date,
    isDone = isDone,
    createdAtEpochMillis = createdAtEpochMillis,
    repeatDays = repeatDaysMask.toDayOfWeekSet(),
)
