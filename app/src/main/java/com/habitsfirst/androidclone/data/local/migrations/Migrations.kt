package com.habitsfirst.androidclone.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real, hand-written [Migration]s covering every version transition
 * [AppDatabase][com.habitsfirst.androidclone.data.local.AppDatabase] has ever been through,
 * 1 through 10 -- see the version-history comment above `@Database` there for the
 * user-facing story of each step; this file is the literal DDL/DML for it.
 *
 * Each `MIGRATION_x_y` is backed by a `MIGRATION_x_y_SQL` statement list so the exact same
 * statements can be replayed against a plain JDBC SQLite connection from a JVM test
 * (`MigrationsSqlTest`, in `app/src/test`) without needing Room's instrumented
 * `MigrationTestHelper` -- see that test's KDoc for why both exist.
 *
 * Versions 2 and 3 never shipped as their own on-disk schema (see AppDatabase's v2/v3/v4
 * comment: two branches independently used those numbers before a merge bumped straight to
 * v4), so no real device has ever been at exactly v2 or v3. The migrations below still
 * apply the v2 and v3 changes as their own discrete steps rather than folding everything
 * into MIGRATION_1_4, because that's strictly safer: it degrades gracefully if a stray
 * install (e.g. a sideloaded PR build) really was left at v2 or v3, and composes to the
 * exact same v4 end state either way.
 */

/**
 * SQL for [MIGRATION_1_2]: added
 * [com.habitsfirst.androidclone.data.local.entity.HabitEntity.verificationPrompt]
 * / `verificationExampleImagePath`, and
 * [com.habitsfirst.androidclone.data.local.entity.HabitCompletionEntity.verificationImagePath]
 * / `verificationReasoning` -- the photo-verification proof-photo fields. All four are
 * nullable with no backfill needed for existing rows.
 */
internal val MIGRATION_1_2_SQL: List<String> = listOf(
    "ALTER TABLE `habits` ADD COLUMN `verificationPrompt` TEXT",
    "ALTER TABLE `habits` ADD COLUMN `verificationExampleImagePath` TEXT",
    "ALTER TABLE `habit_completions` ADD COLUMN `verificationImagePath` TEXT",
    "ALTER TABLE `habit_completions` ADD COLUMN `verificationReasoning` TEXT",
)

/** v1 -> v2, see [MIGRATION_1_2_SQL]. */
val MIGRATION_1_2: Migration = sqlMigration(1, 2, MIGRATION_1_2_SQL)

/**
 * SQL for [MIGRATION_2_3]: added [com.habitsfirst.androidclone.data.local.entity.HabitEntity.kind]
 * (backfilled to `GATING`, the app's original implicit behavior, for every existing habit)
 * and `expiresAfterDate`, plus the new `streak_scars` and `todos` tables.
 *
 * `kind`'s `DEFAULT 'GATING'` here must keep matching
 * `HabitEntity.kind`'s `@ColumnInfo(defaultValue = "'GATING'")` -- see the KDoc there.
 */
internal val MIGRATION_2_3_SQL: List<String> = listOf(
    "ALTER TABLE `habits` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'GATING'",
    "ALTER TABLE `habits` ADD COLUMN `expiresAfterDate` TEXT",
    "CREATE TABLE IF NOT EXISTS `streak_scars` (`date` TEXT NOT NULL, `reason` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`date`))",
    "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `date` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)",
)

/** v2 -> v3, see [MIGRATION_2_3_SQL]. */
val MIGRATION_2_3: Migration = sqlMigration(2, 3, MIGRATION_2_3_SQL)

/**
 * SQL for [MIGRATION_3_4]: added
 * [com.habitsfirst.androidclone.data.local.entity.HabitEntity.easeInOrder] (onboarding
 * "ease into it" ramp). Nullable, no backfill needed.
 */
internal val MIGRATION_3_4_SQL: List<String> = listOf(
    "ALTER TABLE `habits` ADD COLUMN `easeInOrder` INTEGER",
)

/** v3 -> v4, see [MIGRATION_3_4_SQL]. */
val MIGRATION_3_4: Migration = sqlMigration(3, 4, MIGRATION_3_4_SQL)

/**
 * SQL for [MIGRATION_4_5]: added `TodoEntity.repeatDaysMask` (backfilled to 0, meaning
 * "not recurring", for every existing todo) plus the `todo_completions` table, for
 * day-of-week-recurring todos. Both are reverted by [MIGRATION_5_6] below -- todos turned
 * out to be a bad fit for day-of-week recurrence -- but are applied here first to mirror
 * exactly what shipped, in case a stray v4 install needs the same round trip.
 */
internal val MIGRATION_4_5_SQL: List<String> = listOf(
    "ALTER TABLE `todos` ADD COLUMN `repeatDaysMask` INTEGER NOT NULL DEFAULT 0",
    "CREATE TABLE IF NOT EXISTS `todo_completions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `todoId` INTEGER NOT NULL, `date` TEXT NOT NULL, `isDone` INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_todo_completions_todoId_date` ON `todo_completions` (`todoId`, `date`)",
)

/** v4 -> v5, see [MIGRATION_4_5_SQL]. */
val MIGRATION_4_5: Migration = sqlMigration(4, 5, MIGRATION_4_5_SQL)

/**
 * SQL for [MIGRATION_5_6]: reverted `TodoEntity` to a plain one-off today-or-tomorrow task
 * -- dropped `repeatDaysMask` (SQLite can't drop a column in place on this project's
 * minimum SQLite version, so `todos` is recreated without it, copying every other column
 * across) and the now-unused `todo_completions` table; added
 * [com.habitsfirst.androidclone.data.local.entity.HabitEntity.scheduledDaysMask]
 * (backfilled to 0, meaning "every day", for every existing habit) so day-of-week
 * recurrence lives on habits instead.
 *
 * `scheduledDaysMask`'s `DEFAULT 0` here must keep matching
 * `HabitEntity.scheduledDaysMask`'s `@ColumnInfo(defaultValue = "0")` -- see the KDoc there.
 */
internal val MIGRATION_5_6_SQL: List<String> = listOf(
    "CREATE TABLE `todos_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `date` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)",
    "INSERT INTO `todos_new` (`id`, `title`, `date`, `isDone`, `createdAtEpochMillis`) " +
        "SELECT `id`, `title`, `date`, `isDone`, `createdAtEpochMillis` FROM `todos`",
    "DROP TABLE `todos`",
    "ALTER TABLE `todos_new` RENAME TO `todos`",
    "DROP TABLE IF EXISTS `todo_completions`",
    "ALTER TABLE `habits` ADD COLUMN `scheduledDaysMask` INTEGER NOT NULL DEFAULT 0",
)

/** v5 -> v6, see [MIGRATION_5_6_SQL]. */
val MIGRATION_5_6: Migration = sqlMigration(5, 6, MIGRATION_5_6_SQL)

/**
 * SQL for [MIGRATION_6_7]: added `block_lists`/`blocked_domains` for URL blocking
 * (premade porn/social lists plus user-defined custom lists). Both tables are new, so
 * there's no existing data to backfill.
 */
internal val MIGRATION_6_7_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `block_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `source` TEXT NOT NULL, `blockMode` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `blocked_domains` (`listId` TEXT NOT NULL, `domain` TEXT NOT NULL, PRIMARY KEY(`listId`, `domain`), FOREIGN KEY(`listId`) REFERENCES `block_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_blocked_domains_listId` ON `blocked_domains` (`listId`)",
)

/** v6 -> v7, see [MIGRATION_6_7_SQL]. */
val MIGRATION_6_7: Migration = sqlMigration(6, 7, MIGRATION_6_7_SQL)

/**
 * SQL for [MIGRATION_7_8]: folded `HabitType.IMAGE_VERIFICATION` into `HabitType.CUSTOM`
 * as a `HabitEntity.requiresPhotoVerification` toggle. `IMAGE_VERIFICATION` was a real,
 * distinct `HabitType` through v7 (see commit 3bb7b1f's `HabitType.kt`) -- every existing
 * habit of that type must be rewritten to `type = 'CUSTOM'` with
 * `requiresPhotoVerification = 1`, or `HabitType.valueOf(type)` (no fallback, unlike
 * `kind`) throws on the very next app launch and crashes every screen that loads that
 * habit. Every *other* existing habit is backfilled to `requiresPhotoVerification = 0`
 * (via the column's own `DEFAULT 0`) since there's no way to tell which pre-v8 `CUSTOM`
 * habits, if any, would have wanted it on -- matching the feature's own framing as an
 * opt-in toggle. `requiresPhotoVerification` itself is short-lived -- it's dropped again
 * by [MIGRATION_8_9] below.
 */
internal val MIGRATION_7_8_SQL: List<String> = listOf(
    "ALTER TABLE `habits` ADD COLUMN `requiresPhotoVerification` INTEGER NOT NULL DEFAULT 0",
    "UPDATE `habits` SET `type` = 'CUSTOM', `requiresPhotoVerification` = 1 WHERE `type` = 'IMAGE_VERIFICATION'",
)

/** v7 -> v8, see [MIGRATION_7_8_SQL]. */
val MIGRATION_7_8: Migration = sqlMigration(7, 8, MIGRATION_7_8_SQL)

/**
 * SQL for [MIGRATION_8_9]: simplified habit types -- `EXERCISE_MINUTES` and
 * `MEDITATION_MINUTES` merged into one generic `TIMED_MINUTES`; `CUSTOM` split back into
 * `PHOTO` (was the `requiresPhotoVerification` toggle from v8, now always on for this
 * type) and `TALLY` (the toggle-off case, a plain manual check-in) -- then
 * `HabitEntity.requiresPhotoVerification` is dropped, its meaning now fully captured by
 * `type` itself.
 *
 * This is the one migration in this file that rewrites existing data, not just structure:
 * every `habits.type` string naming a type that no longer exists must be remapped *before*
 * `requiresPhotoVerification` -- the very column that decided which of `PHOTO`/`TALLY` a
 * `CUSTOM` habit becomes -- is gone. Getting the `UPDATE` order and predicates here wrong
 * would silently corrupt which habits are photo-gated, which is worse than the destructive
 * fallback this whole file replaces -- see `MigrationsSqlTest` for the data-preservation
 * assertions this is checked against.
 */
internal val MIGRATION_8_9_SQL: List<String> = listOf(
    // 1) Remap `type` while `requiresPhotoVerification` (needed to disambiguate CUSTOM)
    //    still exists.
    "UPDATE `habits` SET `type` = 'TIMED_MINUTES' WHERE `type` IN ('EXERCISE_MINUTES', 'MEDITATION_MINUTES')",
    "UPDATE `habits` SET `type` = 'PHOTO' WHERE `type` = 'CUSTOM' AND `requiresPhotoVerification` = 1",
    "UPDATE `habits` SET `type` = 'TALLY' WHERE `type` = 'CUSTOM' AND `requiresPhotoVerification` = 0",
    // 2) Drop `requiresPhotoVerification` by recreating `habits` without it -- SQLite on
    //    this project's minimum version can't drop a column in place. Column list/order/
    //    types/defaults below must match HabitEntity's current @Entity-generated schema
    //    exactly (see app/schemas/.../9.json, the source of truth this was checked
    //    against).
    "CREATE TABLE `habits_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `targetValue` INTEGER NOT NULL, `targetPackageName` TEXT, `targetAppLabel` TEXT, `sortOrder` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `verificationPrompt` TEXT, `verificationExampleImagePath` TEXT, `kind` TEXT NOT NULL DEFAULT 'GATING', `expiresAfterDate` TEXT, `easeInOrder` INTEGER, `scheduledDaysMask` INTEGER NOT NULL DEFAULT 0)",
    "INSERT INTO `habits_new` (`id`, `name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`, `verificationPrompt`, `verificationExampleImagePath`, `kind`, `expiresAfterDate`, `easeInOrder`, `scheduledDaysMask`) " +
        "SELECT `id`, `name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`, `verificationPrompt`, `verificationExampleImagePath`, `kind`, `expiresAfterDate`, `easeInOrder`, `scheduledDaysMask` FROM `habits`",
    "DROP TABLE `habits`",
    "ALTER TABLE `habits_new` RENAME TO `habits`",
)

/** v8 -> v9, see [MIGRATION_8_9_SQL]. */
val MIGRATION_8_9: Migration = sqlMigration(8, 9, MIGRATION_8_9_SQL)

/**
 * SQL for [MIGRATION_9_10]: added `block_attempts` (impulse-control stat -- one row per
 * instant the block screen actually covered a blocked app/URL, see
 * [com.habitsfirst.androidclone.data.local.entity.BlockAttemptEntity]) and
 * [com.habitsfirst.androidclone.data.local.entity.TodoEntity.completedAtEpochMillis]
 * (nullable, no backfill -- there's no way to know when an already-done todo was
 * actually completed, so existing done todos simply read as "no timing data" rather
 * than being guessed at). Both are purely additive -- no existing column changes type,
 * gets dropped, or needs a table recreate -- checked against the build-generated
 * `app/schemas/.../10.json` (verified byte-identical against a rebuild), same as every
 * migration above.
 */
internal val MIGRATION_9_10_SQL: List<String> = listOf(
    "ALTER TABLE `todos` ADD COLUMN `completedAtEpochMillis` INTEGER",
    "CREATE TABLE IF NOT EXISTS `block_attempts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `target` TEXT NOT NULL, `date` TEXT NOT NULL, `timestampEpochMillis` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_block_attempts_date` ON `block_attempts` (`date`)",
)

/** v9 -> v10, see [MIGRATION_9_10_SQL]. */
val MIGRATION_9_10: Migration = sqlMigration(9, 10, MIGRATION_9_10_SQL)

/**
 * Every real migration this database has, in order, for
 * [com.habitsfirst.androidclone.di.AppModule.provideDatabase] to install via
 * `addMigrations(*ALL_MIGRATIONS)`.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
)

/** Builds a [Migration] that just runs [statements] in order via [SupportSQLiteDatabase.execSQL]. */
private fun sqlMigration(startVersion: Int, endVersion: Int, statements: List<String>): Migration =
    object : Migration(startVersion, endVersion) {
        override fun migrate(db: SupportSQLiteDatabase) {
            statements.forEach(db::execSQL)
        }
    }
