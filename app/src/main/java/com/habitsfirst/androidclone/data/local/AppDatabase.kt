package com.habitsfirst.androidclone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.habitsfirst.androidclone.data.local.dao.AccountabilityBuddyDao
import com.habitsfirst.androidclone.data.local.dao.BlockAttemptDao
import com.habitsfirst.androidclone.data.local.dao.BlockedAppDao
import com.habitsfirst.androidclone.data.local.dao.BlockedDomainDao
import com.habitsfirst.androidclone.data.local.dao.BlockListDao
import com.habitsfirst.androidclone.data.local.dao.HabitCompletionDao
import com.habitsfirst.androidclone.data.local.dao.HabitDao
import com.habitsfirst.androidclone.data.local.dao.PendingStatsSyncDao
import com.habitsfirst.androidclone.data.local.dao.StreakScarDao
import com.habitsfirst.androidclone.data.local.dao.TodoDao
import com.habitsfirst.androidclone.data.local.entity.AccountabilityBuddyEntity
import com.habitsfirst.androidclone.data.local.entity.BlockAttemptEntity
import com.habitsfirst.androidclone.data.local.entity.BlockedAppEntity
import com.habitsfirst.androidclone.data.local.entity.BlockedDomainEntity
import com.habitsfirst.androidclone.data.local.entity.BlockListEntity
import com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity
import com.habitsfirst.androidclone.data.local.entity.HabitEntity
import com.habitsfirst.androidclone.data.local.entity.PendingStatsSyncEntity
import com.habitsfirst.androidclone.data.local.entity.StreakScarEntity
import com.habitsfirst.androidclone.data.local.entity.TodoEntity

@Database(
    entities = [
        HabitEntity::class,
        HabitCompletionEntity::class,
        BlockedAppEntity::class,
        StreakScarEntity::class,
        TodoEntity::class,
        BlockListEntity::class,
        BlockedDomainEntity::class,
        BlockAttemptEntity::class,
        AccountabilityBuddyEntity::class,
        PendingStatsSyncEntity::class,
    ],
    // v2 (two independent branches merged into this one): added HabitEntity.kind/
    // expiresAfterDate, streak_scars, todos, and separately HabitCompletionEntity's
    // verificationImagePath/verificationReasoning + HabitEntity's verificationPrompt/
    // verificationExampleImagePath.
    // v3: added HabitEntity.easeInOrder (onboarding "ease into it" ramp).
    // v4: merge of the above two lines -- bumped past both so either prior install's
    // schema gets rebuilt.
    // v5: added TodoEntity.repeatDaysMask + todo_completions, for day-of-week-recurring
    // todos (e.g. "hoover" every Sunday). Superseded by v6 below -- todos turned out to
    // be a bad fit for day-of-week recurrence.
    // v6: reverted TodoEntity to a plain one-off today-or-tomorrow task (dropped
    // repeatDaysMask and todo_completions); added HabitEntity.scheduledDaysMask so
    // day-of-week recurrence (e.g. "hoover" every Sunday) lives on habits instead.
    // v7: added block_lists/blocked_domains for URL blocking (premade porn/social lists
    // plus user-defined custom lists).
    // v8: folded HabitType.IMAGE_VERIFICATION into HabitType.CUSTOM as a
    // HabitEntity.requiresPhotoVerification toggle, so photo proof is an option on a
    // custom check-in rather than a separate habit type.
    // v9: simplified habit types -- EXERCISE_MINUTES and MEDITATION_MINUTES merged into
    // one generic TIMED_MINUTES (built-in timer for anything timed); CUSTOM split back
    // into two: PHOTO (was the requiresPhotoVerification toggle from v8, now always on
    // for this type -- dropped HabitEntity.requiresPhotoVerification) and TALLY (the
    // toggle-off case, a plain manual check-in).
    // v10: added block_attempts (impulse-control stat: every time the block screen
    // actually covers a blocked app/URL, not just the block-list config) and
    // TodoEntity.completedAtEpochMillis (time-to-complete stat for todos). See
    // MIGRATION_9_10 in data/local/migrations/Migrations.kt.
    // v11 (2026-09-05): added accountability_buddies + pending_stats_sync -- the local
    // cache/outbox for the accountability-buddy backend scaffolding (see
    // data/repository/AccountabilityRepository.kt). No default backend exists yet. See
    // MIGRATION_10_11 in data/local/migrations/Migrations.kt.
    //
    // Every step through 10->11 now has a real Migration in
    // data/local/migrations/Migrations.kt, wired in by di/AppModule.kt's
    // provideDatabase(). exportSchema is on and app/schemas/ is checked in as the
    // ground truth those migrations are written and tested against -- see
    // Migrations.kt's file-level KDoc and MigrationsSqlTest before touching either.
    version = 11,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitCompletionDao(): HabitCompletionDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun streakScarDao(): StreakScarDao
    abstract fun todoDao(): TodoDao
    abstract fun blockListDao(): BlockListDao
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun blockAttemptDao(): BlockAttemptDao
    abstract fun accountabilityBuddyDao(): AccountabilityBuddyDao
    abstract fun pendingStatsSyncDao(): PendingStatsSyncDao

    companion object {
        const val DATABASE_NAME = "habits_first.db"
    }
}
