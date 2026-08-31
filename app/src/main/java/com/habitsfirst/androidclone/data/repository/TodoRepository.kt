package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.TodoDao
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
    fun observeForDate(date: String): Flow<List<Todo>> =
        todoDao.observeForDate(date).map { list -> list.map { it.toDomain() } }

    /**
     * Today's todos, re-derived as the calendar date actually changes -- unlike
     * `observeForDate(DateProvider.todayString())`, which (as a Flow held for a whole
     * ViewModel's lifetime) would otherwise keep watching whatever date it happened to
     * be built on. See [DateProvider.currentDateFlow].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeForToday(): Flow<List<Todo>> =
        DateProvider.currentDateFlow().flatMapLatest { date -> observeForDate(date) }

    suspend fun addTodo(title: String, date: String = DateProvider.todayString()) {
        if (title.isBlank()) return
        todoDao.upsert(Todo(title = title.trim(), date = date).toEntity())
    }

    suspend fun setDone(id: Long, done: Boolean) {
        todoDao.setDone(id, done)
    }

    suspend fun delete(todo: Todo) {
        todoDao.delete(todo.toEntity())
    }

    suspend fun hasTodosForDate(date: String = DateProvider.todayString()): Boolean =
        todoDao.getCountForDate(date) > 0
}
