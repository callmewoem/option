package com.habitsfirst.androidclone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.habitsfirst.androidclone.data.local.dao.BlockedAppDao
import com.habitsfirst.androidclone.data.local.dao.HabitCompletionDao
import com.habitsfirst.androidclone.data.local.dao.HabitDao
import com.habitsfirst.androidclone.data.local.dao.StreakScarDao
import com.habitsfirst.androidclone.data.local.dao.TodoDao
import com.habitsfirst.androidclone.data.local.entity.BlockedAppEntity
import com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity
import com.habitsfirst.androidclone.data.local.entity.HabitEntity
import com.habitsfirst.androidclone.data.local.entity.StreakScarEntity
import com.habitsfirst.androidclone.data.local.entity.TodoEntity

@Database(
    entities = [
        HabitEntity::class,
        HabitCompletionEntity::class,
        BlockedAppEntity::class,
        StreakScarEntity::class,
        TodoEntity::class,
    ],
    // v2 (two independent branches merged into this one): added HabitEntity.kind/
    // expiresAfterDate, streak_scars, todos, and separately HabitCompletionEntity's
    // verificationImagePath/verificationReasoning + HabitEntity's verificationPrompt/
    // verificationExampleImagePath.
    // v3: added HabitEntity.easeInOrder (onboarding "ease into it" ramp).
    // v4: merge of the above two lines -- bumped past both so either prior install's
    // schema gets rebuilt.
    // No migration is written since the app hasn't shipped yet -- provideDatabase() in
    // di/AppModule.kt uses fallbackToDestructiveMigration(), which is fine pre-release
    // but must be replaced with a real Migration before this ships with real user data.
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitCompletionDao(): HabitCompletionDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun streakScarDao(): StreakScarDao
    abstract fun todoDao(): TodoDao

    companion object {
        const val DATABASE_NAME = "habits_first.db"
    }
}
