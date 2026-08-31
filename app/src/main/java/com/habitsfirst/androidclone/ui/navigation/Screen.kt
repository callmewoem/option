package com.habitsfirst.androidclone.ui.navigation

import com.habitsfirst.androidclone.domain.model.HabitKind

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")

    data object OnboardingWelcome : Screen("onboarding/welcome")
    data object OnboardingPickApps : Screen("onboarding/pick_apps")
    data object OnboardingPickHabits : Screen("onboarding/pick_habits")
    data object OnboardingPermissions : Screen("onboarding/permissions")

    data object Home : Screen("home")
    data object Habits : Screen("habits")
    data object AppPicker : Screen("app_picker")
    data object Settings : Screen("settings")

    data object AddHabit : Screen("habit/new?kind={kind}") {
        fun createRoute(kind: HabitKind = HabitKind.GATING) = "habit/new?kind=${kind.name}"
    }
    data object EditHabit : Screen("habit/{habitId}") {
        fun createRoute(habitId: Long) = "habit/$habitId"
    }
    data object MeditationTimer : Screen("habit/{habitId}/meditate") {
        fun createRoute(habitId: Long) = "habit/$habitId/meditate"
    }

    companion object {
        const val ARG_HABIT_ID = "habitId"
        const val ARG_KIND = "kind"
    }
}
