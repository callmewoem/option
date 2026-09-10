package com.habitsfirst.androidclone.ui.navigation

import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")

    /**
     * [replay] is true when this is a re-run from Settings ("Replay onboarding") rather
     * than the real first-run flow -- read by [com.habitsfirst.androidclone.ui.onboarding.OnboardingViewModel]
     * to skip persisting anything at the end instead of redoing initial setup.
     */
    data object OnboardingWelcome : Screen("onboarding/welcome?replay={replay}") {
        fun createRoute(replay: Boolean = false) = "onboarding/welcome?replay=$replay"
    }
    data object OnboardingUsageAccess : Screen("onboarding/usage_access")
    data object OnboardingPickApps : Screen("onboarding/pick_apps")
    data object OnboardingPickHabits : Screen("onboarding/pick_habits")
    data object OnboardingPermissions : Screen("onboarding/permissions")
    data object OnboardingCurfewCheckIn : Screen("onboarding/curfew_checkin")

    data object Home : Screen("home")
    data object Habits : Screen("habits")
    data object Todo : Screen("todo")
    data object AppPicker : Screen("app_picker")
    data object UrlBlockList : Screen("url_block_list")
    data object Settings : Screen("settings")
    data object Diagnostics : Screen("diagnostics")

    data object AddHabit : Screen("habit/new?kind={kind}&type={type}") {
        /** [type] preselects the new habit's type (e.g. deep-linking straight into a [HabitType.PHOTO] setup) -- omit to leave the form at its own default. */
        fun createRoute(kind: HabitKind = HabitKind.GATING, type: HabitType? = null) =
            "habit/new?kind=${kind.name}&type=${type?.name ?: ""}"
    }
    data object EditHabit : Screen("habit/{habitId}") {
        fun createRoute(habitId: Long) = "habit/$habitId"
    }
    data object TimedHabitTimer : Screen("habit/{habitId}/timer") {
        fun createRoute(habitId: Long) = "habit/$habitId/timer"
    }
    data object VerifyHabit : Screen("habit/{habitId}/verify") {
        fun createRoute(habitId: Long) = "habit/$habitId/verify"
    }
    data object ScanTag : Screen("habit/{habitId}/scan") {
        fun createRoute(habitId: Long) = "habit/$habitId/scan"
    }
    data object ProofOfLife : Screen("proof_of_life")

    companion object {
        const val ARG_HABIT_ID = "habitId"
        const val ARG_KIND = "kind"
        const val ARG_TYPE = "type"
        const val ARG_REPLAY = "replay"
    }
}
