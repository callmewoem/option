package com.habitsfirst.androidclone.data.local.entity

import androidx.room.Entity
import com.habitsfirst.androidclone.domain.model.BlockedApp

@Entity(tableName = "blocked_apps", primaryKeys = ["packageName"])
data class BlockedAppEntity(
    val packageName: String,
    val appLabel: String,
    val isEnabled: Boolean = true,
    val addedAtEpochMillis: Long = System.currentTimeMillis(),
)

fun BlockedAppEntity.toDomain(): BlockedApp = BlockedApp(
    packageName = packageName,
    appLabel = appLabel,
    isEnabled = isEnabled,
    addedAtEpochMillis = addedAtEpochMillis,
)

fun BlockedApp.toEntity(): BlockedAppEntity = BlockedAppEntity(
    packageName = packageName,
    appLabel = appLabel,
    isEnabled = isEnabled,
    addedAtEpochMillis = addedAtEpochMillis,
)
