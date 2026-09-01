package com.habitsfirst.androidclone.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.habitsfirst.androidclone.domain.model.HabitKind
import com.habitsfirst.androidclone.domain.model.HabitType
import com.habitsfirst.androidclone.ui.apppicker.AppPickerScreen
import com.habitsfirst.androidclone.ui.habit.AddEditHabitScreen
import com.habitsfirst.androidclone.ui.habit.ImageVerificationScreen
import com.habitsfirst.androidclone.ui.habit.MeditationTimerScreen
import com.habitsfirst.androidclone.ui.habits.HabitsScreen
import com.habitsfirst.androidclone.ui.home.HomeScreen
import com.habitsfirst.androidclone.ui.onboarding.OnboardingPermissionsScreen
import com.habitsfirst.androidclone.ui.onboarding.OnboardingPickAppsScreen
import com.habitsfirst.androidclone.ui.onboarding.OnboardingPickHabitsScreen
import com.habitsfirst.androidclone.ui.onboarding.OnboardingViewModel
import com.habitsfirst.androidclone.ui.onboarding.OnboardingWelcomeScreen
import com.habitsfirst.androidclone.ui.proofoflife.ProofOfLifeScreen
import com.habitsfirst.androidclone.ui.settings.SettingsScreen

@Composable
fun HabitsFirstNavHost() {
    val navController = rememberNavController()
    val splashViewModel: SplashViewModel = hiltViewModel()
    val splashState by splashViewModel.uiState.collectAsStateWithLifecycle()

    if (!splashState.isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (splashState.onboardingComplete) Screen.Home.route else Screen.OnboardingWelcome.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.OnboardingWelcome.route) {
            OnboardingWelcomeScreen(
                onGetStarted = { navController.navigate(Screen.OnboardingPickApps.route) },
            )
        }
        composable(Screen.OnboardingPickApps.route) {
            val onboardingViewModel: OnboardingViewModel =
                hiltViewModel(navController.getBackStackEntry(Screen.OnboardingWelcome.route))
            OnboardingPickAppsScreen(
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Screen.OnboardingPickHabits.route) },
                viewModel = onboardingViewModel,
            )
        }
        composable(Screen.OnboardingPickHabits.route) {
            val onboardingViewModel: OnboardingViewModel =
                hiltViewModel(navController.getBackStackEntry(Screen.OnboardingWelcome.route))
            OnboardingPickHabitsScreen(
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Screen.OnboardingPermissions.route) },
                viewModel = onboardingViewModel,
            )
        }
        composable(Screen.OnboardingPermissions.route) {
            val onboardingViewModel: OnboardingViewModel =
                hiltViewModel(navController.getBackStackEntry(Screen.OnboardingWelcome.route))
            OnboardingPermissionsScreen(
                onBack = { navController.popBackStack() },
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.OnboardingWelcome.route) { inclusive = true }
                    }
                },
                viewModel = onboardingViewModel,
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                onAddHabit = { navController.navigate(Screen.AddHabit.createRoute(HabitKind.GATING)) },
                onOpenHabit = { habitId -> navController.navigate(Screen.MeditationTimer.createRoute(habitId)) },
                onVerifyHabit = { habitId -> navController.navigate(Screen.VerifyHabit.createRoute(habitId)) },
                onCheckIn = { navController.navigate(Screen.ProofOfLife.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onManageApps = { navController.navigate(Screen.AppPicker.route) },
                onSetUpPhotoVerification = {
                    navController.navigate(Screen.AddHabit.createRoute(HabitKind.GATING, HabitType.IMAGE_VERIFICATION))
                },
            )
        }

        composable(Screen.Habits.route) {
            HabitsScreen(
                navController = navController,
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.AppPicker.route) {
            AppPickerScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAddHabit = { navController.navigate(Screen.AddHabit.createRoute(HabitKind.GATING)) },
                onEditHabit = { habitId -> navController.navigate(Screen.EditHabit.createRoute(habitId)) },
                onManageApps = { navController.navigate(Screen.AppPicker.route) },
            )
        }

        composable(
            route = Screen.AddHabit.route,
            arguments = listOf(
                navArgument(Screen.ARG_KIND) {
                    type = NavType.StringType
                    defaultValue = HabitKind.GATING.name
                },
                navArgument(Screen.ARG_TYPE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            AddEditHabitScreen(
                onDone = { navController.popBackStack() },
                onOpenMeditationTimer = { habitId ->
                    navController.navigate(Screen.MeditationTimer.createRoute(habitId))
                },
            )
        }
        composable(
            route = Screen.EditHabit.route,
            arguments = listOf(navArgument(Screen.ARG_HABIT_ID) { type = NavType.StringType }),
        ) {
            AddEditHabitScreen(
                onDone = { navController.popBackStack() },
                onOpenMeditationTimer = { habitId ->
                    navController.navigate(Screen.MeditationTimer.createRoute(habitId))
                },
            )
        }
        composable(
            route = Screen.MeditationTimer.route,
            arguments = listOf(navArgument(Screen.ARG_HABIT_ID) { type = NavType.StringType }),
        ) {
            MeditationTimerScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = Screen.VerifyHabit.route,
            arguments = listOf(navArgument(Screen.ARG_HABIT_ID) { type = NavType.StringType }),
        ) {
            ImageVerificationScreen(
                onDone = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.ProofOfLife.route) {
            ProofOfLifeScreen(
                onDone = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
    }
}
