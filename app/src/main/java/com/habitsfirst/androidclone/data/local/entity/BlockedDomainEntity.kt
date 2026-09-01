package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** One domain in a [BlockListSource.CUSTOM][com.habitsfirst.androidclone.domain.model.BlockListSource.CUSTOM] list. Deleting the list cascades here. */
@Entity(
    tableName = "blocked_domains",
    primaryKeys = ["listId", "domain"],
    foreignKeys = [
        ForeignKey(
            entity = BlockListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listId")],
)
data class BlockedDomainEntity(
    val listId: String,
    val domain: String,
)
