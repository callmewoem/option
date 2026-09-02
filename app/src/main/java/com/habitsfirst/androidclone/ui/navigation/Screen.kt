package com.habitsfirst.androidclone.ui.navigation

import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")

    data object OnboardingWelcome : Screen("onboarding/welcome")
    data object OnboardingPickApps : Screen("onboarding/pick_apps")
    data object OnboardingPickHabits : Screen("onboarding/pick_habits")
    data object OnboardingPermissions : Screen("onboarding/permissions")

    data object Home : Screen("home")
    data object Habits : Screen("habits")
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
    data object ProofOfLife : Screen("proof_of_life")

    companion object {
        const val ARG_HABIT_ID = "habitId"
        const val ARG_KIND = "kind"
        const val ARG_TYPE = "type"
    }
}
