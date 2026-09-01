package com.habitsfirst.androidclone.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")

    data object OnboardingWelcome : Screen("onboarding/welcome")
    data object OnboardingPickApps : Screen("onboarding/pick_apps")
    data object OnboardingPickHabits : Screen("onboarding/pick_habits")
    data object OnboardingPermissions : Screen("onboarding/permissions")

    data object Home : Screen("home")
    data object AppPicker : Screen("app_picker")
    data object Settings : Screen("settings")

    data object AddHabit : Screen("habit/new")
    data object EditHabit : Screen("habit/{habitId}") {
        fun createRoute(habitId: Long) = "habit/$habitId"
    }
    data object MeditationTimer : Screen("habit/{habitId}/meditate") {
        fun createRoute(habitId: Long) = "habit/$habitId/meditate"
    }
    data object VerifyHabit : Screen("habit/{habitId}/verify") {
        fun createRoute(habitId: Long) = "habit/$habitId/verify"
    }

    companion object {
        const val ARG_HABIT_ID = "habitId"
    }
}
