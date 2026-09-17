package com.habitsfirst.androidclone.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.habitsfirst.androidclone.domain.model.AppBlockMode
import com.habitsfirst.androidclone.domain.model.SubscriptionTier
import com.habitsfirst.androidclone.domain.model.ThemeMode
import com.habitsfirst.androidclone.domain.model.ThemeVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small pieces of app state that don't belong in Room: onboarding progress and the
 * cached streak count (recomputed lazily, cached here so the home screen can render
 * it instantly).
 */
@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val ONBOARDING_COMPLETED_DATE = stringPreferencesKey("onboarding_completed_date")
        val HAS_SEEN_HOME_TOUR = booleanPreferencesKey("has_seen_home_tour")
        val HAS_DISMISSED_PHOTO_VERIFICATION_PROMPT = booleanPreferencesKey("has_dismissed_photo_verification_prompt")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val CACHED_STREAK = intPreferencesKey("cached_streak")
        val CACHED_STREAK_DATE = stringPreferencesKey("cached_streak_date")
        val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
        val WAKATIME_API_KEY = stringPreferencesKey("wakatime_api_key")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val THEME_MODE = stringPreferencesKey("theme_mode") // ThemeMode.name
        val THEME_VARIANT = stringPreferencesKey("theme_variant")
        val UNLOCKED_THEME_VARIANTS = stringSetPreferencesKey("unlocked_theme_variants")
        val GRACE_TOKEN_COUNT = intPreferencesKey("grace_token_count")
        val TASK_SKIP_TOKEN_COUNT = intPreferencesKey("task_skip_token_count")
        val LAST_LOOTBOX_AWARDED_DATE = stringPreferencesKey("last_lootbox_awarded_date")
        val PENALTY_LOCKED_UNTIL_EPOCH_MILLIS = longPreferencesKey("penalty_locked_until_epoch_millis")
        val GRACE_UNLOCK_UNTIL_EPOCH_MILLIS = longPreferencesKey("grace_unlock_until_epoch_millis")
        val LOCKOUT_UNTIL_EPOCH_MILLIS = longPreferencesKey("lockout_until_epoch_millis")
        val GOLD_STAR_DATES = stringSetPreferencesKey("gold_star_dates")
        val BEDTIME_LOCK_ENABLED = booleanPreferencesKey("bedtime_lock_enabled")
        val BEDTIME_START = stringPreferencesKey("bedtime_start") // "HH:mm"
        val BEDTIME_END = stringPreferencesKey("bedtime_end") // "HH:mm"
        val MORNING_TODO_REMINDER_ENABLED = booleanPreferencesKey("morning_todo_reminder_enabled")
        val MORNING_TODO_REMINDER_TIME = stringPreferencesKey("morning_todo_reminder_time") // "HH:mm"
        val LAST_MORNING_REMINDER_SENT_DATE = stringPreferencesKey("last_morning_reminder_sent_date")
        val LAST_APP_RESUME_DATE = stringPreferencesKey("last_app_resume_date")
        val APP_BLOCK_MODE = stringPreferencesKey("app_block_mode") // AppBlockMode.name
        val LIMITED_UNBLOCK_ENABLED = booleanPreferencesKey("limited_unblock_enabled")
        val LIMITED_UNBLOCK_WINDOW_DATE = stringPreferencesKey("limited_unblock_window_date")
        val LIMITED_UNBLOCK_WINDOW_STARTED_AT_EPOCH_MILLIS = longPreferencesKey("limited_unblock_window_started_at_epoch_millis")
        val HARD_MODE_ENABLED = booleanPreferencesKey("hard_mode_enabled")
        val HARD_MODE_TOGGLE_LOCKED_UNTIL_EPOCH_MILLIS = longPreferencesKey("hard_mode_toggle_locked_until_epoch_millis")
        val HARD_MODE_FRIEND_LOCKED_UNTIL_EPOCH_MILLIS = longPreferencesKey("hard_mode_friend_locked_until_epoch_millis")
        val HARD_MODE_PIN_SALT = stringPreferencesKey("hard_mode_pin_salt")
        val HARD_MODE_PIN_HASH = stringPreferencesKey("hard_mode_pin_hash")
        val HARD_MODE_PIN_FAILED_ATTEMPTS = intPreferencesKey("hard_mode_pin_failed_attempts")
        val HARD_MODE_PIN_ATTEMPTS_LOCKED_UNTIL_EPOCH_MILLIS = longPreferencesKey("hard_mode_pin_attempts_locked_until_epoch_millis")
        val EASE_IN_STREAK_LENGTH = intPreferencesKey("ease_in_streak_length")
        val PROOF_OF_LIFE_ENABLED = booleanPreferencesKey("proof_of_life_enabled")
        val PROOF_OF_LIFE_TIME = stringPreferencesKey("proof_of_life_time") // "HH:mm"
        val PROOF_OF_LIFE_WINDOW_MINUTES = intPreferencesKey("proof_of_life_window_minutes")
        val PROOF_OF_LIFE_CONFIRMED_DATE = stringPreferencesKey("proof_of_life_confirmed_date")
        val PROOF_OF_LIFE_LAST_PENALIZED_DATE = stringPreferencesKey("proof_of_life_last_penalized_date")
        val HEALTH_CONNECT_SYNC_ENABLED = booleanPreferencesKey("health_connect_sync_enabled")
        val SUBSCRIPTION_TIER = stringPreferencesKey("subscription_tier") // SubscriptionTier.name
        val SUBSCRIPTION_EXPIRES_AT = longPreferencesKey("subscription_expires_at_epoch_millis")
        val LAST_USAGE_SYNC_AT_EPOCH_MILLIS = longPreferencesKey("last_usage_sync_at_epoch_millis")
        val LAST_USAGE_SYNC_HABIT_COUNT = intPreferencesKey("last_usage_sync_habit_count")
        val LAST_USAGE_SYNC_ERROR = stringPreferencesKey("last_usage_sync_error")
        val ACCOUNTABILITY_BASE_URL = stringPreferencesKey("accountability_base_url")
        val SHARE_DAILY_STATS_ENABLED = booleanPreferencesKey("share_daily_stats_enabled")
        val MY_PAIRING_CODE = stringPreferencesKey("my_pairing_code")
        val WEEKLY_DIGEST_ENABLED = booleanPreferencesKey("weekly_digest_enabled")
        val WEEKLY_DIGEST_DAY_OF_WEEK = stringPreferencesKey("weekly_digest_day_of_week") // DayOfWeek.name
        val WEEKLY_DIGEST_TIME = stringPreferencesKey("weekly_digest_time") // "HH:mm"
        val LAST_WEEKLY_DIGEST_SENT_DATE = stringPreferencesKey("last_weekly_digest_sent_date")
        val LIMITED_UNBLOCK_WINDOW_MINUTES = intPreferencesKey("limited_unblock_window_minutes")
        val LIMITED_UNBLOCK_STREAK_BONUS_ENABLED = booleanPreferencesKey("limited_unblock_streak_bonus_enabled")
        val LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY = intPreferencesKey("limited_unblock_streak_bonus_minutes_per_day")
    }

    val isOnboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    /** Whether the short spotlight tour on Home's first post-onboarding visit has been dismissed. */
    val hasSeenHomeTour: Flow<Boolean> =
        dataStore.data.map { it[Keys.HAS_SEEN_HOME_TOUR] ?: false }

    suspend fun setHasSeenHomeTour(seen: Boolean) {
        dataStore.edit { it[Keys.HAS_SEEN_HOME_TOUR] = seen }
    }

    /** The calendar date onboarding finished on, so Home can offer a same-day-only nudge (e.g. the photo verification prompt). Null before onboarding completes. */
    val onboardingCompletedDate: Flow<String?> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED_DATE] }

    suspend fun setOnboardingCompletedDate(date: String) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED_DATE] = date }
    }

    /** Whether the day-one "try photo verification" nudge on Home has been dismissed or acted on. */
    val hasDismissedPhotoVerificationPrompt: Flow<Boolean> =
        dataStore.data.map { it[Keys.HAS_DISMISSED_PHOTO_VERIFICATION_PROMPT] ?: false }

    suspend fun setHasDismissedPhotoVerificationPrompt(dismissed: Boolean) {
        dataStore.edit { it[Keys.HAS_DISMISSED_PHOTO_VERIFICATION_PROMPT] = dismissed }
    }

    val areNotificationsEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    val cachedStreak: Flow<Pair<Int, String?>> = dataStore.data.map {
        (it[Keys.CACHED_STREAK] ?: 0) to it[Keys.CACHED_STREAK_DATE]
    }

    suspend fun setCachedStreak(days: Int, forDate: String) {
        dataStore.edit {
            it[Keys.CACHED_STREAK] = days
            it[Keys.CACHED_STREAK_DATE] = forDate
        }
    }

    /** The user's own Anthropic API key, used to verify photos for [com.habitsfirst.androidclone.domain.model.HabitType.PHOTO] habits. */
    val anthropicApiKey: Flow<String?> = dataStore.data.map { it[Keys.ANTHROPIC_API_KEY] }

    suspend fun setAnthropicApiKey(key: String?) {
        dataStore.edit {
            if (key.isNullOrBlank()) it.remove(Keys.ANTHROPIC_API_KEY) else it[Keys.ANTHROPIC_API_KEY] = key.trim()
        }
    }

    /** The user's own WakaTime API key, used to sync [com.habitsfirst.androidclone.domain.model.HabitType.WAKATIME_CODING_MINUTES] habits. */
    val wakaTimeApiKey: Flow<String?> = dataStore.data.map { it[Keys.WAKATIME_API_KEY] }

    suspend fun setWakaTimeApiKey(key: String?) {
        dataStore.edit {
            if (key.isNullOrBlank()) it.remove(Keys.WAKATIME_API_KEY) else it[Keys.WAKATIME_API_KEY] = key.trim()
        }
    }

    /**
     * The user's own GitHub personal access token (optional -- a
     * [com.habitsfirst.androidclone.domain.model.HabitType.GITHUB_CONTRIBUTION] habit
     * still works without one, checking only public activity at GitHub's unauthenticated
     * rate limit). Setting one raises that limit and lets private contributions count too.
     */
    val githubToken: Flow<String?> = dataStore.data.map { it[Keys.GITHUB_TOKEN] }

    suspend fun setGithubToken(token: String?) {
        dataStore.edit {
            if (token.isNullOrBlank()) it.remove(Keys.GITHUB_TOKEN) else it[Keys.GITHUB_TOKEN] = token.trim()
        }
    }

    // -- Appearance -------------------------------------------------------------------

    /** Light/Dark/System for "app mode" screens -- see [ThemeMode]. Enforcement-mode screens are unaffected. */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { ThemeMode.fromId(it[Keys.THEME_MODE]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    // -- Theme (lootbox-unlockable) --------------------------------------------------

    val selectedThemeVariantId: Flow<String> = dataStore.data.map { it[Keys.THEME_VARIANT] ?: "" }

    suspend fun setSelectedThemeVariantId(variantId: String) {
        dataStore.edit { it[Keys.THEME_VARIANT] = variantId }
    }

    /** Every variant the user has unlocked from the lootbox. [ThemeVariant.Moss] and [ThemeVariant.Modern] are always available, free. */
    val unlockedThemeVariantIds: Flow<Set<String>> =
        dataStore.data.map {
            (it[Keys.UNLOCKED_THEME_VARIANTS] ?: emptySet()) + setOf(ThemeVariant.Moss.name, ThemeVariant.Modern.name)
        }

    suspend fun unlockThemeVariant(variantId: String) {
        dataStore.edit {
            it[Keys.UNLOCKED_THEME_VARIANTS] = (it[Keys.UNLOCKED_THEME_VARIANTS] ?: emptySet()) + variantId
        }
    }

    // -- Lootbox rewards ----------------------------------------------------------------

    val graceTokenCount: Flow<Int> = dataStore.data.map { it[Keys.GRACE_TOKEN_COUNT] ?: 0 }
    val taskSkipTokenCount: Flow<Int> = dataStore.data.map { it[Keys.TASK_SKIP_TOKEN_COUNT] ?: 0 }

    suspend fun addGraceTokens(count: Int) {
        dataStore.edit { it[Keys.GRACE_TOKEN_COUNT] = (it[Keys.GRACE_TOKEN_COUNT] ?: 0) + count }
    }

    suspend fun addTaskSkipTokens(count: Int) {
        dataStore.edit { it[Keys.TASK_SKIP_TOKEN_COUNT] = (it[Keys.TASK_SKIP_TOKEN_COUNT] ?: 0) + count }
    }

    /** Returns true and consumes a token if one was available. */
    suspend fun consumeGraceToken(): Boolean {
        var consumed = false
        dataStore.edit {
            val current = it[Keys.GRACE_TOKEN_COUNT] ?: 0
            if (current > 0) {
                it[Keys.GRACE_TOKEN_COUNT] = current - 1
                consumed = true
            }
        }
        return consumed
    }

    suspend fun consumeTaskSkipToken(): Boolean {
        var consumed = false
        dataStore.edit {
            val current = it[Keys.TASK_SKIP_TOKEN_COUNT] ?: 0
            if (current > 0) {
                it[Keys.TASK_SKIP_TOKEN_COUNT] = current - 1
                consumed = true
            }
        }
        return consumed
    }

    /** Guards against awarding more than one lootbox per calendar day. */
    val lastLootboxAwardedDate: Flow<String?> = dataStore.data.map { it[Keys.LAST_LOOTBOX_AWARDED_DATE] }

    suspend fun setLastLootboxAwardedDate(date: String) {
        dataStore.edit { it[Keys.LAST_LOOTBOX_AWARDED_DATE] = date }
    }

    // -- Penalties ------------------------------------------------------------------

    /** Blocked apps stay locked until this instant even after habits are complete. 0 = no active penalty. */
    val penaltyLockedUntilEpochMillis: Flow<Long> =
        dataStore.data.map { it[Keys.PENALTY_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L }

    suspend fun extendPenaltyLock(untilEpochMillis: Long) {
        dataStore.edit {
            val current = it[Keys.PENALTY_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L
            it[Keys.PENALTY_LOCKED_UNTIL_EPOCH_MILLIS] = maxOf(current, untilEpochMillis)
        }
    }

    suspend fun clearPenaltyLock() {
        dataStore.edit { it[Keys.PENALTY_LOCKED_UNTIL_EPOCH_MILLIS] = 0L }
    }

    /** A redeemed grace-period token bypasses habit/penalty locks (never bedtime) until this instant. */
    val graceUnlockUntilEpochMillis: Flow<Long> =
        dataStore.data.map { it[Keys.GRACE_UNLOCK_UNTIL_EPOCH_MILLIS] ?: 0L }

    suspend fun setGraceUnlockUntil(untilEpochMillis: Long) {
        dataStore.edit { it[Keys.GRACE_UNLOCK_UNTIL_EPOCH_MILLIS] = untilEpochMillis }
    }

    /** See [com.habitsfirst.androidclone.data.repository.LockoutRepository]. 0 = no active lockout. */
    val lockoutUntilEpochMillis: Flow<Long> = dataStore.data.map { it[Keys.LOCKOUT_UNTIL_EPOCH_MILLIS] ?: 0L }

    suspend fun setLockoutUntil(untilEpochMillis: Long) {
        dataStore.edit { it[Keys.LOCKOUT_UNTIL_EPOCH_MILLIS] = untilEpochMillis }
    }

    /** Dates cosmetically starred by a GOLD_STAR lootbox reward -- purely decorative on the heatmap. */
    val goldStarDates: Flow<Set<String>> = dataStore.data.map { it[Keys.GOLD_STAR_DATES] ?: emptySet() }

    suspend fun addGoldStarDate(date: String) {
        dataStore.edit { it[Keys.GOLD_STAR_DATES] = (it[Keys.GOLD_STAR_DATES] ?: emptySet()) + date }
    }

    // -- Bedtime lock -----------------------------------------------------------------

    data class BedtimeSettings(val enabled: Boolean, val start: String, val end: String)

    val bedtimeSettings: Flow<BedtimeSettings> = dataStore.data.map {
        BedtimeSettings(
            enabled = it[Keys.BEDTIME_LOCK_ENABLED] ?: false,
            start = it[Keys.BEDTIME_START] ?: "22:30",
            end = it[Keys.BEDTIME_END] ?: "06:30",
        )
    }

    suspend fun setBedtimeSettings(enabled: Boolean, start: String, end: String) {
        dataStore.edit {
            it[Keys.BEDTIME_LOCK_ENABLED] = enabled
            it[Keys.BEDTIME_START] = start
            it[Keys.BEDTIME_END] = end
        }
    }

    // -- Daily todo reminder -----------------------------------------------------------

    data class MorningReminderSettings(val enabled: Boolean, val time: String)

    val morningTodoReminderSettings: Flow<MorningReminderSettings> = dataStore.data.map {
        MorningReminderSettings(
            enabled = it[Keys.MORNING_TODO_REMINDER_ENABLED] ?: true,
            time = it[Keys.MORNING_TODO_REMINDER_TIME] ?: "08:00",
        )
    }

    suspend fun setMorningTodoReminderSettings(enabled: Boolean, time: String) {
        dataStore.edit {
            it[Keys.MORNING_TODO_REMINDER_ENABLED] = enabled
            it[Keys.MORNING_TODO_REMINDER_TIME] = time
        }
    }

    val lastMorningReminderSentDate: Flow<String?> =
        dataStore.data.map { it[Keys.LAST_MORNING_REMINDER_SENT_DATE] }

    suspend fun setLastMorningReminderSentDate(date: String) {
        dataStore.edit { it[Keys.LAST_MORNING_REMINDER_SENT_DATE] = date }
    }

    /**
     * The date the app was last resumed to the foreground (cold launch counts as a
     * resume) -- how [com.habitsfirst.androidclone.AppViewModel] tells "the user just
     * opened the app for the first time today" from "they switched tabs" or "they
     * briefly checked another app and came right back", to decide whether to surface
     * yesterday's undone todos.
     */
    val lastAppResumeDate: Flow<String?> =
        dataStore.data.map { it[Keys.LAST_APP_RESUME_DATE] }

    suspend fun setLastAppResumeDate(date: String) {
        dataStore.edit { it[Keys.LAST_APP_RESUME_DATE] = date }
    }

    // -- App block mode -----------------------------------------------------------------

    /** Whether the app picker's selected packages are locked (blacklist, the default) or exempt from locking (whitelist). */
    val appBlockMode: Flow<AppBlockMode> = dataStore.data.map {
        it[Keys.APP_BLOCK_MODE]?.let { name -> runCatching { AppBlockMode.valueOf(name) }.getOrNull() } ?: AppBlockMode.BLACKLIST
    }

    suspend fun setAppBlockMode(mode: AppBlockMode) {
        dataStore.edit { it[Keys.APP_BLOCK_MODE] = mode.name }
    }

    // -- Limited unblocking -------------------------------------------------------------

    /** See [com.habitsfirst.androidclone.data.repository.LimitedUnblockRepository]. */
    val isLimitedUnblockEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.LIMITED_UNBLOCK_ENABLED] ?: false }

    suspend fun setLimitedUnblockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.LIMITED_UNBLOCK_ENABLED] = enabled }
    }

    /** The date [habitsCompleteUnlockWindowStartedAtEpochMillis] was last stamped for -- lets a stale stamp from an earlier day be told apart from today's. */
    val habitsCompleteUnlockWindowDate: Flow<String?> = dataStore.data.map { it[Keys.LIMITED_UNBLOCK_WINDOW_DATE] }

    /** The instant [LimitedUnblockRepository] first noticed today's habits complete -- its unlock window runs from here. */
    val habitsCompleteUnlockWindowStartedAtEpochMillis: Flow<Long> =
        dataStore.data.map { it[Keys.LIMITED_UNBLOCK_WINDOW_STARTED_AT_EPOCH_MILLIS] ?: 0L }

    suspend fun stampHabitsCompleteUnlockWindowStart(date: String, startedAtEpochMillis: Long) {
        dataStore.edit {
            it[Keys.LIMITED_UNBLOCK_WINDOW_DATE] = date
            it[Keys.LIMITED_UNBLOCK_WINDOW_STARTED_AT_EPOCH_MILLIS] = startedAtEpochMillis
        }
    }

    // -- Hard mode --------------------------------------------------------------------

    /** Hard mode: gating habits and blocked apps can be added but never removed or loosened. */
    val isHardModeEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.HARD_MODE_ENABLED] ?: false }

    /** Instant the next hard-mode toggle (either direction) becomes allowed. 0 = no cooldown pending. */
    val hardModeToggleLockedUntilEpochMillis: Flow<Long> =
        dataStore.data.map { it[Keys.HARD_MODE_TOGGLE_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L }

    /**
     * The friend-lock chosen when hard mode was last turned on: [lockedUntilEpochMillis] is 0 once hard mode is
     * off, [Long.MAX_VALUE] for a lock with no time limit (see [isPermanent]), or an instant it self-expires at.
     * [pinSet] is whether a PIN can also end it early (see [unlockHardModeWithPin]); [pinAttemptsLockedUntilEpochMillis]
     * is the cooldown from too many wrong guesses, 0 when none is pending.
     */
    data class HardModeFriendLock(
        val lockedUntilEpochMillis: Long,
        val pinSet: Boolean,
        val pinAttemptsLockedUntilEpochMillis: Long,
    ) {
        val isPermanent: Boolean get() = lockedUntilEpochMillis == Long.MAX_VALUE

        fun isActive(nowEpochMillis: Long = System.currentTimeMillis()): Boolean = lockedUntilEpochMillis > nowEpochMillis
    }

    val hardModeFriendLock: Flow<HardModeFriendLock> = dataStore.data.map { prefs ->
        HardModeFriendLock(
            lockedUntilEpochMillis = prefs[Keys.HARD_MODE_FRIEND_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L,
            pinSet = prefs[Keys.HARD_MODE_PIN_HASH] != null,
            pinAttemptsLockedUntilEpochMillis = prefs[Keys.HARD_MODE_PIN_ATTEMPTS_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L,
        )
    }

    /**
     * Turning hard mode on grants a one-time batch of grace tokens to ease into it; turning it back off doesn't
     * claw them back. Either direction starts a [HARD_MODE_TOGGLE_COOLDOWN_DAYS]-day cooldown before it can be
     * toggled again -- otherwise hard mode's restrictions could just be switched off whenever they bite, and
     * switching back on would even re-farm the entry grace tokens.
     *
     * Turning it on also arms a friend-lock: [lockDurationDays] days from now, or forever if null (see
     * [HardModeFriendLock.isPermanent]) -- either way it can't be turned back off until that instant, unless
     * [pin] is set, in which case whoever holds it can end the lock early with [unlockHardModeWithPin]. A
     * permanent lock with no pin would have no way out at all, so that combination is rejected. [pin] and
     * [lockDurationDays] are ignored when [enabled] is false; turning hard mode off this way (as opposed to
     * [unlockHardModeWithPin]) only succeeds once the friend-lock itself has already lapsed.
     *
     * Returns true if the toggle took effect, false if it was rejected because the toggle cooldown or the
     * friend-lock from the last time hasn't expired yet.
     */
    suspend fun setHardModeEnabled(
        enabled: Boolean,
        lockDurationDays: Int? = HARD_MODE_TOGGLE_COOLDOWN_DAYS,
        pin: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (enabled) {
            require(lockDurationDays == null || lockDurationDays > 0) {
                "lockDurationDays must be positive, or null for a lock with no time limit"
            }
            require(lockDurationDays != null || !pin.isNullOrBlank()) {
                "a lock with no time limit needs a PIN -- otherwise hard mode could never be turned back off"
            }
            pin?.let {
                require(it.length in HARD_MODE_PIN_MIN_LENGTH..HARD_MODE_PIN_MAX_LENGTH && it.all(Char::isDigit)) {
                    "pin must be $HARD_MODE_PIN_MIN_LENGTH-$HARD_MODE_PIN_MAX_LENGTH digits"
                }
            }
        }

        var applied = false
        dataStore.edit { prefs ->
            val wasEnabled = prefs[Keys.HARD_MODE_ENABLED] ?: false
            if (enabled == wasEnabled) return@edit
            val toggleLockedUntil = prefs[Keys.HARD_MODE_TOGGLE_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L
            if (nowEpochMillis < toggleLockedUntil) return@edit
            if (!enabled) {
                val friendLockedUntil = prefs[Keys.HARD_MODE_FRIEND_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L
                if (nowEpochMillis < friendLockedUntil) return@edit
            }

            prefs[Keys.HARD_MODE_ENABLED] = enabled
            prefs[Keys.HARD_MODE_TOGGLE_LOCKED_UNTIL_EPOCH_MILLIS] =
                nowEpochMillis + HARD_MODE_TOGGLE_COOLDOWN_DAYS * DAY_MILLIS
            if (enabled) {
                prefs[Keys.GRACE_TOKEN_COUNT] = (prefs[Keys.GRACE_TOKEN_COUNT] ?: 0) + HARD_MODE_ENTRY_GRACE_TOKENS
                prefs[Keys.HARD_MODE_FRIEND_LOCKED_UNTIL_EPOCH_MILLIS] =
                    if (lockDurationDays == null) Long.MAX_VALUE else nowEpochMillis + lockDurationDays * DAY_MILLIS
                if (pin.isNullOrBlank()) {
                    prefs.remove(Keys.HARD_MODE_PIN_SALT)
                    prefs.remove(Keys.HARD_MODE_PIN_HASH)
                } else {
                    val (salt, hash) = hashPin(pin)
                    prefs[Keys.HARD_MODE_PIN_SALT] = salt
                    prefs[Keys.HARD_MODE_PIN_HASH] = hash
                }
            } else {
                prefs[Keys.HARD_MODE_FRIEND_LOCKED_UNTIL_EPOCH_MILLIS] = 0L
                prefs.remove(Keys.HARD_MODE_PIN_SALT)
                prefs.remove(Keys.HARD_MODE_PIN_HASH)
            }
            prefs[Keys.HARD_MODE_PIN_FAILED_ATTEMPTS] = 0
            prefs.remove(Keys.HARD_MODE_PIN_ATTEMPTS_LOCKED_UNTIL_EPOCH_MILLIS)
            applied = true
        }
        return applied
    }

    /** What [unlockHardModeWithPin] did with a guess. */
    sealed interface HardModePinResult {
        /** The PIN matched -- hard mode is now off and the friend-lock cleared. */
        data object Unlocked : HardModePinResult

        /** No PIN was ever set for the current friend-lock, so there's nothing to check against. */
        data object NoPinSet : HardModePinResult

        /** Wrong PIN; [attemptsRemaining] guesses left before a cooldown kicks in. */
        data class WrongPin(val attemptsRemaining: Int) : HardModePinResult

        /** Too many wrong guesses in a row -- locked out of trying again until [retryAtEpochMillis]. */
        data class TooManyAttempts(val retryAtEpochMillis: Long) : HardModePinResult
    }

    /**
     * Checks [pin] against the PIN set when hard mode's current friend-lock was armed. A match turns hard mode
     * off immediately, regardless of how much of [HardModeFriendLock.lockedUntilEpochMillis] is left -- this is
     * the whole point of setting a PIN: the friend holding it can let the user back in early. A wrong guess
     * costs one of [HARD_MODE_PIN_ATTEMPTS_PER_ROUND] attempts; running out starts a cooldown that grows every
     * time it's hit again (see [HARD_MODE_PIN_COOLDOWNS_MINUTES]), so a 4-digit PIN can't just be brute-forced
     * on-device by the person it's meant to be keeping out.
     */
    suspend fun unlockHardModeWithPin(pin: String, nowEpochMillis: Long = System.currentTimeMillis()): HardModePinResult {
        var result: HardModePinResult = HardModePinResult.NoPinSet
        dataStore.edit { prefs ->
            val salt = prefs[Keys.HARD_MODE_PIN_SALT]
            val hash = prefs[Keys.HARD_MODE_PIN_HASH]
            if (salt == null || hash == null) {
                result = HardModePinResult.NoPinSet
                return@edit
            }

            val attemptsLockedUntil = prefs[Keys.HARD_MODE_PIN_ATTEMPTS_LOCKED_UNTIL_EPOCH_MILLIS] ?: 0L
            if (nowEpochMillis < attemptsLockedUntil) {
                result = HardModePinResult.TooManyAttempts(attemptsLockedUntil)
                return@edit
            }

            if (pinMatches(pin, salt, hash)) {
                prefs[Keys.HARD_MODE_ENABLED] = false
                prefs[Keys.HARD_MODE_FRIEND_LOCKED_UNTIL_EPOCH_MILLIS] = 0L
                prefs[Keys.HARD_MODE_TOGGLE_LOCKED_UNTIL_EPOCH_MILLIS] = nowEpochMillis + HARD_MODE_TOGGLE_COOLDOWN_DAYS * DAY_MILLIS
                prefs.remove(Keys.HARD_MODE_PIN_SALT)
                prefs.remove(Keys.HARD_MODE_PIN_HASH)
                prefs[Keys.HARD_MODE_PIN_FAILED_ATTEMPTS] = 0
                prefs.remove(Keys.HARD_MODE_PIN_ATTEMPTS_LOCKED_UNTIL_EPOCH_MILLIS)
                result = HardModePinResult.Unlocked
            } else {
                val failed = (prefs[Keys.HARD_MODE_PIN_FAILED_ATTEMPTS] ?: 0) + 1
                prefs[Keys.HARD_MODE_PIN_FAILED_ATTEMPTS] = failed
                val remainingInRound = HARD_MODE_PIN_ATTEMPTS_PER_ROUND - (failed - 1) % HARD_MODE_PIN_ATTEMPTS_PER_ROUND - 1
                if (remainingInRound <= 0) {
                    val round = failed / HARD_MODE_PIN_ATTEMPTS_PER_ROUND - 1
                    val cooldownMinutes = HARD_MODE_PIN_COOLDOWNS_MINUTES.getOrElse(round) { HARD_MODE_PIN_COOLDOWNS_MINUTES.last() }
                    val lockedUntil = nowEpochMillis + cooldownMinutes * 60_000L
                    prefs[Keys.HARD_MODE_PIN_ATTEMPTS_LOCKED_UNTIL_EPOCH_MILLIS] = lockedUntil
                    result = HardModePinResult.TooManyAttempts(lockedUntil)
                } else {
                    result = HardModePinResult.WrongPin(remainingInRound)
                }
            }
        }
        return result
    }

    private fun hashPin(pin: String): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return salt.toHex() to sha256(salt + pin.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun pinMatches(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        val candidateHashHex = sha256(saltHex.hexToBytes() + pin.toByteArray(Charsets.UTF_8)).toHex()
        return MessageDigest.isEqual(candidateHashHex.toByteArray(Charsets.UTF_8), expectedHashHex.toByteArray(Charsets.UTF_8))
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // -- Onboarding "ease into it" ramp ------------------------------------------------

    /** Consecutive completed days required before the ramp's next habit is promoted to GATING. */
    val easeInStreakLength: Flow<Int> =
        dataStore.data.map { it[Keys.EASE_IN_STREAK_LENGTH] ?: DEFAULT_EASE_IN_STREAK_LENGTH }

    suspend fun setEaseInStreakLength(days: Int) {
        dataStore.edit { it[Keys.EASE_IN_STREAK_LENGTH] = days.coerceIn(1, 30) }
    }

    // -- Proof-of-life morning check-in -------------------------------------------------

    data class ProofOfLifeSettings(val enabled: Boolean, val time: String, val windowMinutes: Int)

    val proofOfLifeSettings: Flow<ProofOfLifeSettings> = dataStore.data.map {
        ProofOfLifeSettings(
            enabled = it[Keys.PROOF_OF_LIFE_ENABLED] ?: false,
            time = it[Keys.PROOF_OF_LIFE_TIME] ?: "08:00",
            windowMinutes = it[Keys.PROOF_OF_LIFE_WINDOW_MINUTES] ?: DEFAULT_PROOF_OF_LIFE_WINDOW_MINUTES,
        )
    }

    suspend fun setProofOfLifeSettings(enabled: Boolean, time: String, windowMinutes: Int) {
        dataStore.edit {
            it[Keys.PROOF_OF_LIFE_ENABLED] = enabled
            it[Keys.PROOF_OF_LIFE_TIME] = time
            it[Keys.PROOF_OF_LIFE_WINDOW_MINUTES] = windowMinutes.coerceIn(5, 240)
        }
    }

    /** The last date a proof-of-life photo was approved -- "today" means already checked in. */
    val proofOfLifeConfirmedDate: Flow<String?> = dataStore.data.map { it[Keys.PROOF_OF_LIFE_CONFIRMED_DATE] }

    suspend fun setProofOfLifeConfirmedDate(date: String) {
        dataStore.edit { it[Keys.PROOF_OF_LIFE_CONFIRMED_DATE] = date }
    }

    /** Guards [PenaltyRepository]'s missed-check-in penalty against firing more than once a day. */
    val proofOfLifeLastPenalizedDate: Flow<String?> = dataStore.data.map { it[Keys.PROOF_OF_LIFE_LAST_PENALIZED_DATE] }

    suspend fun setProofOfLifeLastPenalizedDate(date: String) {
        dataStore.edit { it[Keys.PROOF_OF_LIFE_LAST_PENALIZED_DATE] = date }
    }

    // -- Health Connect sync -----------------------------------------------------------

    /** Whether the periodic worker should sync steps/exercise habits from Health Connect. Off by default -- turning it on requires the read permissions be granted first (see Settings). */
    val isHealthConnectSyncEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.HEALTH_CONNECT_SYNC_ENABLED] ?: false }

    suspend fun setHealthConnectSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HEALTH_CONNECT_SYNC_ENABLED] = enabled }
    }

    // -- Subscription / entitlement -----------------------------------------------------

    /** Persisted result of the (future) purchase flow. See [com.habitsfirst.androidclone.data.billing.EntitlementRepository]. */
    data class StoredSubscriptionState(val tier: SubscriptionTier, val expiresAtEpochMillis: Long?)

    val subscriptionState: Flow<StoredSubscriptionState> = dataStore.data.map {
        StoredSubscriptionState(
            tier = SubscriptionTier.fromId(it[Keys.SUBSCRIPTION_TIER]),
            expiresAtEpochMillis = it[Keys.SUBSCRIPTION_EXPIRES_AT],
        )
    }

    /** Null [expiresAtEpochMillis] clears the key (e.g. for [SubscriptionTier.LIFETIME] or [SubscriptionTier.NONE], which never expire). */
    suspend fun setSubscriptionState(tier: SubscriptionTier, expiresAtEpochMillis: Long?) {
        dataStore.edit {
            it[Keys.SUBSCRIPTION_TIER] = tier.name
            if (expiresAtEpochMillis == null) it.remove(Keys.SUBSCRIPTION_EXPIRES_AT) else it[Keys.SUBSCRIPTION_EXPIRES_AT] = expiresAtEpochMillis
        }
    }

    // -- App-usage tracking diagnostics -------------------------------------------------

    /**
     * What [com.habitsfirst.androidclone.service.AppUsageSyncer]'s last run (periodic
     * tick, the one-off "refresh now" job, or a manual run from Settings -> Diagnostics)
     * actually did. Without this, there's no way to tell "the background worker never
     * runs" apart from "it runs, finds nothing wrong, and there's a bug elsewhere" --
     * both look identical from the outside (a habit stuck at 0 minutes) but need
     * completely different fixes.
     */
    data class LastUsageSyncInfo(val atEpochMillis: Long?, val habitCount: Int, val error: String?)

    val lastUsageSyncInfo: Flow<LastUsageSyncInfo> = dataStore.data.map {
        LastUsageSyncInfo(
            atEpochMillis = it[Keys.LAST_USAGE_SYNC_AT_EPOCH_MILLIS],
            habitCount = it[Keys.LAST_USAGE_SYNC_HABIT_COUNT] ?: 0,
            error = it[Keys.LAST_USAGE_SYNC_ERROR],
        )
    }

    suspend fun recordUsageSyncOutcome(habitCount: Int, error: String?) {
        dataStore.edit {
            it[Keys.LAST_USAGE_SYNC_AT_EPOCH_MILLIS] = System.currentTimeMillis()
            it[Keys.LAST_USAGE_SYNC_HABIT_COUNT] = habitCount
            if (error == null) it.remove(Keys.LAST_USAGE_SYNC_ERROR) else it[Keys.LAST_USAGE_SYNC_ERROR] = error
        }
    }

    // -- Accountability buddies (backend scaffolding) -----------------------------------

    /**
     * Base URL of the user's own accountability-buddy backend, e.g.
     * "https://example.com/api". No backend ships with the app -- until this is set,
     * every [com.habitsfirst.androidclone.data.remote.AccountabilityApiClient] call
     * fails fast with a clear "no backend configured" error instead of hitting a
     * hardcoded host. Trimmed of a trailing slash isn't done here (see
     * [com.habitsfirst.androidclone.data.remote.HttpAccountabilityApiClient]); blank
     * clears the key, same pattern as [anthropicApiKey].
     */
    val accountabilityBaseUrl: Flow<String?> = dataStore.data.map { it[Keys.ACCOUNTABILITY_BASE_URL] }

    suspend fun setAccountabilityBaseUrl(url: String?) {
        dataStore.edit {
            if (url.isNullOrBlank()) it.remove(Keys.ACCOUNTABILITY_BASE_URL) else it[Keys.ACCOUNTABILITY_BASE_URL] = url.trim()
        }
    }

    /** Whether today's summary is pushed to the configured backend for buddies to see. Off by default. */
    val shareDailyStatsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SHARE_DAILY_STATS_ENABLED] ?: false }

    suspend fun setShareDailyStatsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SHARE_DAILY_STATS_ENABLED] = enabled }
    }

    /** This device's own pairing code, last minted by [com.habitsfirst.androidclone.data.repository.AccountabilityRepository.regeneratePairingCode]. Null until generated once. */
    val myPairingCode: Flow<String?> = dataStore.data.map { it[Keys.MY_PAIRING_CODE] }

    suspend fun setMyPairingCode(code: String?) {
        dataStore.edit {
            if (code.isNullOrBlank()) it.remove(Keys.MY_PAIRING_CODE) else it[Keys.MY_PAIRING_CODE] = code.trim()
        }
    }

    // -- Weekly digest --------------------------------------------------------------

    /**
     * A once-a-week "5/7 days complete, best streak 4 days" recap notification --
     * opt-in and off by default, unlike the daily morning reminder, since it's an
     * extra nudge on top of that one rather than something every install needs.
     */
    data class WeeklyDigestSettings(val enabled: Boolean, val dayOfWeek: DayOfWeek, val time: String)

    val weeklyDigestSettings: Flow<WeeklyDigestSettings> = dataStore.data.map {
        WeeklyDigestSettings(
            enabled = it[Keys.WEEKLY_DIGEST_ENABLED] ?: false,
            dayOfWeek = it[Keys.WEEKLY_DIGEST_DAY_OF_WEEK]
                ?.let { name -> runCatching { DayOfWeek.valueOf(name) }.getOrNull() }
                ?: DayOfWeek.SUNDAY,
            time = it[Keys.WEEKLY_DIGEST_TIME] ?: "18:00",
        )
    }

    suspend fun setWeeklyDigestSettings(enabled: Boolean, dayOfWeek: DayOfWeek, time: String) {
        dataStore.edit {
            it[Keys.WEEKLY_DIGEST_ENABLED] = enabled
            it[Keys.WEEKLY_DIGEST_DAY_OF_WEEK] = dayOfWeek.name
            it[Keys.WEEKLY_DIGEST_TIME] = time
        }
    }

    /** Guards [com.habitsfirst.androidclone.service.WeeklyDigestWorker] against posting the same week's recap twice. */
    val lastWeeklyDigestSentDate: Flow<String?> = dataStore.data.map { it[Keys.LAST_WEEKLY_DIGEST_SENT_DATE] }

    suspend fun setLastWeeklyDigestSentDate(date: String) {
        dataStore.edit { it[Keys.LAST_WEEKLY_DIGEST_SENT_DATE] = date }
    }

    // -- Limited-unblock window customization ------------------------------------------

    /**
     * How long [LimitedUnblockRepository]'s post-completion window lasts. [windowMinutes]
     * replaces what used to be a hardcoded 60. [streakBonusEnabled] adds
     * [streakBonusMinutesPerDay] extra minutes for every day of the user's current streak
     * (see [HabitRepository.computeCurrentStreak]) on top of [windowMinutes] -- a small
     * reward for consistency, capped by [LimitedUnblockRepository] so an especially long
     * streak can't stretch the window out indefinitely.
     */
    data class LimitedUnblockWindowSettings(
        val windowMinutes: Int,
        val streakBonusEnabled: Boolean,
        val streakBonusMinutesPerDay: Int,
    )

    val limitedUnblockWindowSettings: Flow<LimitedUnblockWindowSettings> = dataStore.data.map {
        LimitedUnblockWindowSettings(
            windowMinutes = it[Keys.LIMITED_UNBLOCK_WINDOW_MINUTES] ?: DEFAULT_LIMITED_UNBLOCK_WINDOW_MINUTES,
            streakBonusEnabled = it[Keys.LIMITED_UNBLOCK_STREAK_BONUS_ENABLED] ?: false,
            streakBonusMinutesPerDay = it[Keys.LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY]
                ?: DEFAULT_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY,
        )
    }

    suspend fun setLimitedUnblockWindowMinutes(minutes: Int) {
        dataStore.edit {
            it[Keys.LIMITED_UNBLOCK_WINDOW_MINUTES] =
                minutes.coerceIn(MIN_LIMITED_UNBLOCK_WINDOW_MINUTES, MAX_LIMITED_UNBLOCK_WINDOW_MINUTES)
        }
    }

    suspend fun setLimitedUnblockStreakBonus(enabled: Boolean, minutesPerDay: Int) {
        dataStore.edit {
            it[Keys.LIMITED_UNBLOCK_STREAK_BONUS_ENABLED] = enabled
            it[Keys.LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY] = minutesPerDay.coerceIn(
                MIN_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY,
                MAX_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY,
            )
        }
    }

    companion object {
        const val HARD_MODE_ENTRY_GRACE_TOKENS = 5
        const val HARD_MODE_TOGGLE_COOLDOWN_DAYS = 7

        /** Presets offered on the "turn on hard mode" sheet, alongside a no-time-limit option. */
        val HARD_MODE_LOCK_DURATION_PRESETS_DAYS = listOf(7, 14, 30, 90)
        const val HARD_MODE_PIN_MIN_LENGTH = 4
        const val HARD_MODE_PIN_MAX_LENGTH = 10

        /** Wrong PIN guesses allowed before a cooldown starts. */
        const val HARD_MODE_PIN_ATTEMPTS_PER_ROUND = 5

        /** Cooldown length once a round of wrong guesses runs out, growing each time it happens again; caps at the last entry. */
        val HARD_MODE_PIN_COOLDOWNS_MINUTES = listOf(5, 15, 60, 240, 1_440)
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val DEFAULT_EASE_IN_STREAK_LENGTH = 5
        const val DEFAULT_PROOF_OF_LIFE_WINDOW_MINUTES = 30
        const val DEFAULT_LIMITED_UNBLOCK_WINDOW_MINUTES = 60
        const val DEFAULT_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY = 5

        /**
         * Shared with [LimitedUnblockRepository.computeEffectiveWindowMinutes] (the ceiling
         * on the streak-bonus-boosted window, so it can never exceed what a user could
         * already configure directly) and with the settings-screen stepper's range -- one
         * constant so the three can't drift apart.
         */
        const val MIN_LIMITED_UNBLOCK_WINDOW_MINUTES = 5
        const val MAX_LIMITED_UNBLOCK_WINDOW_MINUTES = 480
        const val MIN_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY = 0
        const val MAX_LIMITED_UNBLOCK_STREAK_BONUS_MINUTES_PER_DAY = 30
    }
}
