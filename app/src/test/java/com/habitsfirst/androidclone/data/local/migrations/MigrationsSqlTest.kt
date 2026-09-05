package com.habitsfirst.androidclone.data.local.migrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Runs [Migrations.kt][com.habitsfirst.androidclone.data.local.migrations]'s raw SQL
 * against a real SQLite engine (`org.xerial:sqlite-jdbc`, a JVM-native build of SQLite --
 * no Android framework and no emulator needed) and checks the resulting table/column set
 * and, for the migrations that touch existing data, that rows come out the way they
 * should.
 *
 * ## Why this instead of `MigrationTestHelper`
 * The idiomatic way to test Room migrations is `androidx.room.testing.MigrationTestHelper`
 * (added as a test dependency per the task, see `app/build.gradle.kts`), replaying each
 * `Migration` through Room itself and diffing the result against the exported schema
 * JSONs in `app/schemas/`. That class resolves its target [android.content.Context] via
 * `androidx.test.platform.app.InstrumentationRegistry`, so it only runs as an
 * *instrumented* test (`androidTest`, `./gradlew connectedAndroidTest`) against a real
 * device or emulator -- and this sandbox has neither. It is **not** wired up here, and
 * running it hasn't been possible in this environment; add it under `app/src/androidTest`
 * (mirroring `MIGRATION_x_y_SQL` below into `db.execSQL(...)` calls, which is already
 * exactly what each `Migration.migrate()` does) once a device is available, rather than
 * treating this class as a substitute for it.
 *
 * What this class verifies instead, for real, on this machine: that every migration's SQL
 * is valid against actual SQLite (catches typos, wrong column/table names, bad DDL for
 * SQLite's specific `ALTER TABLE` limitations), that the full 1->10 chain produces the
 * table/column set [AppDatabase][com.habitsfirst.androidclone.data.local.AppDatabase]'s
 * current entities expect (cross-checked by hand against
 * `app/schemas/com.habitsfirst.androidclone.data.local.AppDatabase/10.json`, generated
 * from those entities), and that the data-rewriting step (v8->v9's habit-type remap) does
 * the right thing on seeded rows. It does not check Room's own schema-identity hash or
 * `@ColumnInfo(defaultValue)` bookkeeping -- there's no substitute for `MigrationTestHelper`
 * (or just running the app against an old on-device database) for that.
 */
class MigrationsSqlTest {

    /**
     * The v1 schema (three tables, no verification/kind/streak/todo columns yet) as it
     * shipped in commit 6cbd3b3 -- the starting point every chain test below replays
     * [MIGRATION_1_2_SQL] onward from.
     */
    private val v1SchemaSql = listOf(
        "CREATE TABLE `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `targetValue` INTEGER NOT NULL, `targetPackageName` TEXT, `targetAppLabel` TEXT, `sortOrder` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)",
        "CREATE TABLE `habit_completions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, `date` TEXT NOT NULL, `currentValue` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER)",
        "CREATE UNIQUE INDEX `index_habit_completions_habitId_date` ON `habit_completions` (`habitId`, `date`)",
        "CREATE TABLE `blocked_apps` (`packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `addedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
    )

    /** Every migration's SQL, 1->2 through 9->10, in order. */
    private val allMigrationSql = listOf(
        MIGRATION_1_2_SQL,
        MIGRATION_2_3_SQL,
        MIGRATION_3_4_SQL,
        MIGRATION_4_5_SQL,
        MIGRATION_5_6_SQL,
        MIGRATION_6_7_SQL,
        MIGRATION_7_8_SQL,
        MIGRATION_8_9_SQL,
        MIGRATION_9_10_SQL,
    )

    private fun newV1Database(): Connection {
        // A fresh, uniquely-named in-memory DB per test -- ":memory:" alone would let
        // JDBC hand back a *shared* cached connection across tests in some drivers.
        val connection = DriverManager.getConnection("jdbc:sqlite:file:${javaClass.simpleName}${System.nanoTime()}?mode=memory&cache=shared")
        connection.createStatement().use { statement ->
            v1SchemaSql.forEach(statement::execute)
        }
        return connection
    }

    private fun Connection.runSql(statements: List<String>) {
        createStatement().use { statement ->
            statements.forEach { sql ->
                try {
                    statement.execute(sql)
                } catch (e: SQLException) {
                    throw AssertionError("Failed executing: $sql", e)
                }
            }
        }
    }

    private fun Connection.tableNames(): Set<String> {
        val names = mutableSetOf<String>()
        createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { rs ->
                while (rs.next()) names += rs.getString("name")
            }
        }
        return names
    }

    private fun Connection.columnNames(table: String): List<String> {
        val names = mutableListOf<String>()
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) names += rs.getString("name")
            }
        }
        return names
    }

    private fun Connection.typesOf(): List<String> {
        val types = mutableListOf<String>()
        createStatement().use { statement ->
            statement.executeQuery("SELECT `type` FROM `habits` ORDER BY `id`").use { rs ->
                while (rs.next()) types += rs.getString("type")
            }
        }
        return types
    }

    @Test
    fun `every migration's SQL is valid and the full chain 1 to 10 lands on the current table set`() {
        newV1Database().use { db ->
            allMigrationSql.forEach { db.runSql(it) }

            assertEquals(
                setOf(
                    "habits", "habit_completions", "blocked_apps", "streak_scars", "todos",
                    "block_lists", "blocked_domains", "block_attempts",
                ),
                db.tableNames(),
            )
            // No leftover recreation-scratch tables from MIGRATION_5_6/MIGRATION_8_9.
            assertFalse(db.tableNames().contains("todos_new"))
            assertFalse(db.tableNames().contains("habits_new"))
            assertFalse(db.tableNames().contains("todo_completions"))

            assertEquals(
                listOf(
                    "id", "name", "type", "targetValue", "targetPackageName", "targetAppLabel",
                    "sortOrder", "createdAtEpochMillis", "isArchived", "verificationPrompt",
                    "verificationExampleImagePath", "kind", "expiresAfterDate", "easeInOrder",
                    "scheduledDaysMask",
                ),
                db.columnNames("habits"),
            )
            assertFalse(
                "requiresPhotoVerification must not survive past v9",
                db.columnNames("habits").contains("requiresPhotoVerification"),
            )
            assertEquals(
                listOf("id", "habitId", "date", "currentValue", "isCompleted", "completedAtEpochMillis", "verificationImagePath", "verificationReasoning"),
                db.columnNames("habit_completions"),
            )
            assertEquals(
                listOf("id", "title", "date", "isDone", "createdAtEpochMillis", "completedAtEpochMillis"),
                db.columnNames("todos"),
            )
            assertFalse("repeatDaysMask must not survive past v6", db.columnNames("todos").contains("repeatDaysMask"))
            assertEquals(listOf("date", "reason", "createdAtEpochMillis"), db.columnNames("streak_scars"))
            assertEquals(
                listOf("id", "name", "source", "blockMode", "isEnabled", "createdAtEpochMillis"),
                db.columnNames("block_lists"),
            )
            assertEquals(listOf("listId", "domain"), db.columnNames("blocked_domains"))
            assertEquals(listOf("id", "target", "date", "timestampEpochMillis"), db.columnNames("block_attempts"))
        }
    }

    @Test
    fun `migration 9 to 10 adds todos completedAtEpochMillis without disturbing existing rows`() {
        newV1Database().use { db ->
            allMigrationSql.take(8).forEach { db.runSql(it) } // 1->2 .. 8->9, i.e. up to v9

            db.runSql(
                listOf(
                    "INSERT INTO `todos` (`title`, `date`, `isDone`, `createdAtEpochMillis`) VALUES ('Water plants', '2026-09-05', 1, 1000)",
                ),
            )

            db.runSql(MIGRATION_9_10_SQL)

            assertEquals(
                listOf("id", "title", "date", "isDone", "createdAtEpochMillis", "completedAtEpochMillis"),
                db.columnNames("todos"),
            )
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT `title`, `completedAtEpochMillis` FROM `todos`").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Water plants", rs.getString("title"))
                    assertEquals(null, rs.getObject("completedAtEpochMillis"))
                    assertFalse(rs.next())
                }
            }
            assertTrue(db.tableNames().contains("block_attempts"))
        }
    }

    @Test
    fun `the habitId,date unique index on habit_completions survives the full chain`() {
        newV1Database().use { db ->
            allMigrationSql.forEach { db.runSql(it) }
            db.runSql(
                listOf(
                    "INSERT INTO `habits` (`name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`) " +
                        "VALUES ('Read', 'TALLY', 0, NULL, NULL, 0, 0, 0)",
                    "INSERT INTO `habit_completions` (`habitId`, `date`, `currentValue`, `isCompleted`) VALUES (1, '2026-09-05', 1, 1)",
                ),
            )
            try {
                db.runSql(listOf("INSERT INTO `habit_completions` (`habitId`, `date`, `currentValue`, `isCompleted`) VALUES (1, '2026-09-05', 1, 1)"))
                fail("Expected the unique (habitId, date) index to reject a duplicate row")
            } catch (expected: AssertionError) {
                // runSql wraps the SQLException from the unique-constraint violation.
            }
        }
    }

    @Test
    fun `migration 7 to 8 remaps the removed IMAGE_VERIFICATION type to CUSTOM with requiresPhotoVerification set`() {
        newV1Database().use { db ->
            allMigrationSql.take(6).forEach { db.runSql(it) } // 1->2 .. 6->7, i.e. up to v7

            db.runSql(
                listOf(
                    "INSERT INTO `habits` (`name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`) " +
                        "VALUES ('Proof of workout', 'IMAGE_VERIFICATION', 0, NULL, NULL, 0, 0, 0)",
                    "INSERT INTO `habits` (`name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`) " +
                        "VALUES ('Make bed', 'CUSTOM', 0, NULL, NULL, 1, 0, 0)",
                ),
            )

            db.runSql(MIGRATION_7_8_SQL)

            data class Row(val type: String, val requiresPhotoVerification: Int)
            val rows = mutableListOf<Row>()
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT `type`, `requiresPhotoVerification` FROM `habits` ORDER BY `sortOrder`").use { rs ->
                    while (rs.next()) rows += Row(rs.getString("type"), rs.getInt("requiresPhotoVerification"))
                }
            }
            assertEquals(listOf(Row("CUSTOM", 1), Row("CUSTOM", 0)), rows)

            // ...and it keeps flowing correctly through the rest of the chain: a v7
            // IMAGE_VERIFICATION habit must land on PHOTO at v9, exactly like a v8 CUSTOM
            // habit that had the toggle on.
            db.runSql(MIGRATION_8_9_SQL)
            assertEquals(listOf("PHOTO", "TALLY"), db.typesOf())
        }
    }

    @Test
    fun `migration 8 to 9 merges EXERCISE_MINUTES and MEDITATION_MINUTES into TIMED_MINUTES`() {
        newV1Database().use { db ->
            allMigrationSql.take(7).forEach { db.runSql(it) } // 1->2 .. 7->8, i.e. up to v8

            db.runSql(
                listOf(
                    "INSERT INTO `habits` (`name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`, `requiresPhotoVerification`) " +
                        "VALUES ('Run', 'EXERCISE_MINUTES', 20, NULL, NULL, 0, 0, 0, 0)",
                    "INSERT INTO `habits` (`name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`, `requiresPhotoVerification`) " +
                        "VALUES ('Meditate', 'MEDITATION_MINUTES', 10, NULL, NULL, 1, 0, 0, 0)",
                    "INSERT INTO `habits` (`name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`, `requiresPhotoVerification`) " +
                        "VALUES ('Steps', 'STEPS', 8000, NULL, NULL, 2, 0, 0, 0)",
                ),
            )

            db.runSql(MIGRATION_8_9_SQL)

            assertEquals(listOf("TIMED_MINUTES", "TIMED_MINUTES", "STEPS"), db.typesOf())
        }
    }

    @Test
    fun `migration 8 to 9 splits CUSTOM into PHOTO or TALLY based on requiresPhotoVerification, then drops that column`() {
        newV1Database().use { db ->
            allMigrationSql.take(7).forEach { db.runSql(it) } // up to v8

            db.runSql(
                listOf(
                    "INSERT INTO `habits` (`name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`, `requiresPhotoVerification`) " +
                        "VALUES ('Proof of workout', 'CUSTOM', 0, NULL, NULL, 0, 0, 0, 1)",
                    "INSERT INTO `habits` (`name`, `type`, `targetValue`, `targetPackageName`, `targetAppLabel`, `sortOrder`, `createdAtEpochMillis`, `isArchived`, `requiresPhotoVerification`) " +
                        "VALUES ('Make bed', 'CUSTOM', 0, NULL, NULL, 1, 0, 0, 0)",
                ),
            )

            db.runSql(MIGRATION_8_9_SQL)

            assertEquals(listOf("PHOTO", "TALLY"), db.typesOf())
            assertFalse(db.columnNames("habits").contains("requiresPhotoVerification"))
        }
    }

    @Test
    fun `migration 5 to 6 drops repeatDaysMask and todo_completions while preserving todo rows`() {
        newV1Database().use { db ->
            allMigrationSql.take(4).forEach { db.runSql(it) } // 1->2 .. 4->5, i.e. up to v5

            db.runSql(
                listOf(
                    "INSERT INTO `todos` (`title`, `date`, `isDone`, `createdAtEpochMillis`, `repeatDaysMask`) VALUES ('Hoover', '2026-09-06', 0, 1000, 64)",
                ),
            )

            db.runSql(MIGRATION_5_6_SQL)

            assertFalse(db.columnNames("todos").contains("repeatDaysMask"))
            assertFalse(db.tableNames().contains("todo_completions"))
            createRowAssertion(db)
        }
    }

    private fun createRowAssertion(db: Connection) {
        db.createStatement().use { statement ->
            statement.executeQuery("SELECT `title`, `date`, `isDone`, `createdAtEpochMillis` FROM `todos`").use { rs ->
                assertTrue(rs.next())
                assertEquals("Hoover", rs.getString("title"))
                assertEquals("2026-09-06", rs.getString("date"))
                assertEquals(0, rs.getInt("isDone"))
                assertEquals(1000, rs.getLong("createdAtEpochMillis"))
                assertFalse(rs.next())
            }
        }
    }

    @Test
    fun `blocked_domains cascades on deleting its block_lists row`() {
        newV1Database().use { db ->
            allMigrationSql.forEach { db.runSql(it) }
            db.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }

            db.runSql(
                listOf(
                    "INSERT INTO `block_lists` (`id`, `name`, `source`, `blockMode`, `isEnabled`, `createdAtEpochMillis`) VALUES ('custom-1', 'My list', 'CUSTOM', 'GATED', 1, 0)",
                    "INSERT INTO `blocked_domains` (`listId`, `domain`) VALUES ('custom-1', 'example.com')",
                    "DELETE FROM `block_lists` WHERE `id` = 'custom-1'",
                ),
            )

            db.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) AS c FROM `blocked_domains`").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(0, rs.getInt("c"))
                }
            }
        }
    }
}
