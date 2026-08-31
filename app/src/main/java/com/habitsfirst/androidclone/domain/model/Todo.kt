package com.habitsfirst.androidclone.domain.model

/** A non-repeating task for a single calendar day. */
data class Todo(
    val id: Long = 0L,
    val title: String,
    val date: String,
    val isDone: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)
