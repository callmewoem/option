package com.habitsfirst.androidclone.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.habitsfirst.androidclone.data.local.AppDatabase
import com.habitsfirst.androidclone.data.local.dao.BlockAttemptDao
import com.habitsfirst.androidclone.data.local.dao.BlockedAppDao
import com.habitsfirst.androidclone.data.local.dao.BlockedDomainDao
import com.habitsfirst.androidclone.data.local.dao.BlockListDao
import com.habitsfirst.androidclone.data.local.dao.HabitCompletionDao
import com.habitsfirst.androidclone.data.local.dao.HabitDao
import com.habitsfirst.androidclone.data.local.dao.StreakScarDao
import com.habitsfirst.androidclone.data.local.dao.TodoDao
import com.habitsfirst.androidclone.data.local.migrations.ALL_MIGRATIONS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "habits_first_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Every version this database has ever shipped (1 through 10, current) has a real
     * [androidx.room.migration.Migration] in `data/local/migrations/Migrations.kt`,
     * wired in below via `ALL_MIGRATIONS` instead of `fallbackToDestructiveMigration()`.
     * Deliberately no destructive fallback beyond that: if a future version bump lands
     * without its own `Migration`, Room throws `IllegalStateException` the first time
     * that build tries to open an older on-device database, rather than silently
     * dropping and recreating every table (wiping the user's habit/todo/streak history
     * with no warning) -- that crash-loudly behavior is the entire point of this setup.
     *
     * So: bumping `AppDatabase.version` past 10 without adding a matching
     * `MIGRATION_10_11` (etc.) here is a bug, not a style choice -- add the migration
     * (mirroring the pattern in Migrations.kt) in the same change that bumps the
     * version, the same way every step 1->10 was done.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()

    @Provides
    fun provideHabitCompletionDao(db: AppDatabase): HabitCompletionDao = db.habitCompletionDao()

    @Provides
    fun provideBlockedAppDao(db: AppDatabase): BlockedAppDao = db.blockedAppDao()

    @Provides
    fun provideStreakScarDao(db: AppDatabase): StreakScarDao = db.streakScarDao()

    @Provides
    fun provideTodoDao(db: AppDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideBlockListDao(db: AppDatabase): BlockListDao = db.blockListDao()

    @Provides
    fun provideBlockedDomainDao(db: AppDatabase): BlockedDomainDao = db.blockedDomainDao()

    @Provides
    fun provideBlockAttemptDao(db: AppDatabase): BlockAttemptDao = db.blockAttemptDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore
}
