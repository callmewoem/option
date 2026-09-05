package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.TodoDao
import com.habitsfirst.androidclone.data.local.entity.TodoEntity
import com.habitsfirst.androidclone.data.local.entity.toDomain
import com.habitsfirst.androidclone.data.local.entity.toEntity
import com.habitsfirst.androidclone.domain.model.Todo
import com.habitsfirst.androidclone.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
) {
    /**
     * Todos due today or tomorrow -- the only two due dates a todo can have (see
     * [Todo]). Re-derived as the calendar date actually changes, so "today" and
     * "tomorrow" don't go stale for a screen (or ViewModel) left open across midnight
     * -- see [DateProvider.currentDateFlow].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUpcoming(): Flow<List<Todo>> =
        DateProvider.currentDateFlow().flatMapLatest { today ->
            val tomorrow = DateProvider.toDateString(DateProvider.fromDateString(today).plusDays(1))
            todoDao.observeForDates(today, tomorrow).map { rows -> rows.map { it.toDomain() } }
        }

    /** Adds a task due today, or tomorrow if [dueTomorrow] is set. */
    suspend fun addTodo(title: String, dueTomorrow: Boolean = false) {
        if (title.isBlank()) return
        val date = if (dueTomorrow) DateProvider.tomorrowString() else DateProvider.todayString()
        todoDao.upsert(TodoEntity(title = title.trim(), date = date))
    }

    suspend fun setDone(todo: Todo, done: Boolean) {
        todoDao.setDone(todo.id, done)
    }

    suspend fun delete(todo: Todo) {
        todoDao.delete(todo.toEntity())
    }

    suspend fun hasTodosForDate(date: String = DateProvider.todayString()): Boolean =
        todoDao.getCountForDate(date) > 0

    /** Every todo due in [startDate]..[endDate] inclusive, oldest first -- the data export's source for todos. */
    suspend fun getTodosInRange(startDate: String, endDate: String): List<Todo> =
        todoDao.getForDateRange(startDate, endDate).map { it.toDomain() }
}
