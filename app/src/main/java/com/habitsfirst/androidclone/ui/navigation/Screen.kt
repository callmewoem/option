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

    data object AddHabit : Screen("habit/new?kind={kind}&type={type}&requiresPhoto={requiresPhoto}") {
        /**
         * [type] preselects the new habit's type; [requiresPhoto] additionally
         * pre-enables the photo-verification toggle on a [HabitType.CUSTOM] habit (e.g.
         * deep-linking straight into a photo-verification setup) -- omit both to leave
         * the form at its own defaults.
         */
        fun createRoute(kind: HabitKind = HabitKind.GATING, type: HabitType? = null, requiresPhoto: Boolean = false) =
            "habit/new?kind=${kind.name}&type=${type?.name ?: ""}&requiresPhoto=$requiresPhoto"
    }
    data object EditHabit : Screen("habit/{habitId}") {
        fun createRoute(habitId: Long) = "habit/$habitId"
    }
    data object MeditationTimer : Screen("habit/{habitId}/meditate") {
        fun createRoute(habitId: Long) = "habit/$habitId/meditate"
    }
    data object VerifyHabit : Screen("habit/{habitId}/verify") {
        fun createRoute(habitId: Long) = "habit/$habitId/verify"
    }
    data object ProofOfLife : Screen("proof_of_life")

    companion object {
        const val ARG_HABIT_ID = "habitId"
        const val ARG_KIND = "kind"
        const val ARG_TYPE = "type"
        const val ARG_REQUIRES_PHOTO = "requiresPhoto"
    }
}
