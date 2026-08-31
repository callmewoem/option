package com.habitsfirst.androidclone.data.repository

import com.habitsfirst.androidclone.data.local.dao.BlockedAppDao
import com.habitsfirst.androidclone.data.local.entity.toDomain
import com.habitsfirst.androidclone.data.local.entity.toEntity
import com.habitsfirst.androidclone.domain.model.BlockedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockedAppRepository @Inject constructor(
    private val blockedAppDao: BlockedAppDao,
) {
    fun observeBlockedApps(): Flow<List<BlockedApp>> =
        blockedAppDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeEnabledPackageNames(): Flow<List<String>> = blockedAppDao.observeEnabledPackageNames()

    suspend fun getEnabledPackageNamesOnce(): Set<String> =
        blockedAppDao.getEnabledOnce().map { it.packageName }.toSet()

    suspend fun setBlocked(packageName: String, appLabel: String, blocked: Boolean) {
        if (blocked) {
            blockedAppDao.upsert(BlockedApp(packageName = packageName, appLabel = appLabel).toEntity())
        } else {
            blockedAppDao.deleteByPackageName(packageName)
        }
    }

    suspend fun toggleEnabled(app: BlockedApp) {
        blockedAppDao.upsert(app.copy(isEnabled = !app.isEnabled).toEntity())
    }

    suspend fun remove(packageName: String) {
        blockedAppDao.deleteByPackageName(packageName)
    }
}
