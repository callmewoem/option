package com.locke.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.locke.app.domain.model.HabitKind
import com.locke.app.domain.model.HabitType
import com.locke.app.domain.model.PremiumFeature
import com.locke.app.ui.apppicker.AppPickerScreen
import com.locke.app.ui.diagnostics.DiagnosticsScreen
import com.locke.app.ui.habit.AddEditHabitScreen
import com.locke.app.ui.habit.ImageVerificationScreen
import com.locke.app.ui.habit.TimedHabitTimerScreen
import com.locke.app.ui.habits.HabitsScreen
import com.locke.app.ui.home.HomeScreen
import com.locke.app.ui.legal.PrivacyPolicyScreen
import com.locke.app.ui.onboarding.OnboardingCurfewCheckInScreen
import com.locke.app.ui.onboarding.OnboardingPaywallScreen
import com.locke.app.ui.onboarding.OnboardingPermissionsScreen
import com.locke.app.ui.onboarding.OnboardingPickAppsScreen
import com.locke.app.ui.onboarding.OnboardingPickHabitsScreen
import com.locke.app.ui.onboarding.OnboardingUsageAccessScreen
import com.locke.app.ui.onboarding.OnboardingViewModel
import com.locke.app.ui.onboarding.OnboardingWelcomeScreen
import com.locke.app.ui.paywall.PaywallScreen
import com.locke.app.ui.proofoflife.ProofOfLifeScreen
import com.locke.app.ui.settings.SettingsScreen
import com.locke.app.ui.todo.TodoScreen
import com.locke.app.ui.urlblock.UrlBlockScreen

@Composable
fun LockeNavHost() {
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
                onGetStarted = { navController.navigate(Screen.OnboardingUsageAccess.route) },
            )
        }
        composable(Screen.OnboardingUsageAccess.route) {
            val onboardingViewModel: OnboardingViewModel =
                hiltViewModel(navController.getBackStackEntry(Screen.OnboardingWelcome.route))
            OnboardingUsageAccessScreen(
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Screen.OnboardingPickApps.route) },
                viewModel = onboardingViewModel,
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
                onContinue = { navController.navigate(Screen.OnboardingCurfewCheckIn.route) },
                viewModel = onboardingViewModel,
            )
        }
        composable(Screen.OnboardingCurfewCheckIn.route) {
            val onboardingViewModel: OnboardingViewModel =
                hiltViewModel(navController.getBackStackEntry(Screen.OnboardingWelcome.route))
            val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
            // ExperimentKeys.ONBOARDING_PAYWALL_STEP's "hidden" variant: finish onboarding
            // straight from here instead of detouring through the Premium pitch step --
            // still reachable later from Settings either way.
            LaunchedEffect(onboardingState.finished) {
                if (onboardingState.finished) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.OnboardingWelcome.route) { inclusive = true }
                    }
                }
            }
            OnboardingCurfewCheckInScreen(
                onBack = { navController.popBackStack() },
                onContinue = {
                    if (onboardingState.showPaywallStep) {
                        navController.navigate(Screen.OnboardingPaywall.route)
                    } else {
                        onboardingViewModel.finishOnboarding()
                    }
                },
                viewModel = onboardingViewModel,
            )
        }
        composable(Screen.OnboardingPaywall.route) {
            val onboardingViewModel: OnboardingViewModel =
                hiltViewModel(navController.getBackStackEntry(Screen.OnboardingWelcome.route))
            OnboardingPaywallScreen(
                onBack = { navController.popBackStack() },
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.OnboardingWelcome.route) { inclusive = true }
                    }
                },
                onboardingViewModel = onboardingViewModel,
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                onAddHabit = { navController.navigate(Screen.AddHabit.createRoute(HabitKind.GATING)) },
                onOpenHabit = { habitId -> navController.navigate(Screen.TimedHabitTimer.createRoute(habitId)) },
                onVerifyHabit = { habitId -> navController.navigate(Screen.VerifyHabit.createRoute(habitId)) },
                onCheckIn = { navController.navigate(Screen.ProofOfLife.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onManageApps = { navController.navigate(Screen.AppPicker.route) },
                onSetUpPhotoVerification = {
                    navController.navigate(Screen.AddHabit.createRoute(HabitKind.GATING, HabitType.PHOTO))
                },
            )
        }

        composable(Screen.Habits.route) {
            HabitsScreen(
                navController = navController,
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.Todo.route) {
            TodoScreen(navController = navController)
        }

        composable(Screen.AppPicker.route) {
            AppPickerScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.UrlBlockList.route) {
            UrlBlockScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAddHabit = { navController.navigate(Screen.AddHabit.createRoute(HabitKind.GATING)) },
                onEditHabit = { habitId -> navController.navigate(Screen.EditHabit.createRoute(habitId)) },
                onManageApps = { navController.navigate(Screen.AppPicker.route) },
                onManageUrls = { navController.navigate(Screen.UrlBlockList.route) },
                onOpenDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                onOpenPaywall = { feature -> navController.navigate(Screen.Paywall.createRoute(feature)) },
                onOpenPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
            )
        }

        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
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
                onOpenTimer = { habitId ->
                    navController.navigate(Screen.TimedHabitTimer.createRoute(habitId))
                },
                onUpgrade = { navController.navigate(Screen.Paywall.createRoute(PremiumFeature.UNLIMITED_HABITS)) },
            )
        }
        composable(
            route = Screen.EditHabit.route,
            arguments = listOf(navArgument(Screen.ARG_HABIT_ID) { type = NavType.StringType }),
        ) {
            AddEditHabitScreen(
                onDone = { navController.popBackStack() },
                onOpenTimer = { habitId ->
                    navController.navigate(Screen.TimedHabitTimer.createRoute(habitId))
                },
                onUpgrade = { navController.navigate(Screen.Paywall.createRoute(PremiumFeature.UNLIMITED_HABITS)) },
            )
        }
        composable(
            route = Screen.TimedHabitTimer.route,
            arguments = listOf(navArgument(Screen.ARG_HABIT_ID) { type = NavType.StringType }),
        ) {
            TimedHabitTimerScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = Screen.VerifyHabit.route,
            arguments = listOf(navArgument(Screen.ARG_HABIT_ID) { type = NavType.StringType }),
        ) {
            ImageVerificationScreen(
                onDone = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Screen.Paywall.createRoute(PremiumFeature.PHOTO_VERIFICATION)) },
            )
        }
        composable(Screen.ProofOfLife.route) {
            ProofOfLifeScreen(
                onDone = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Screen.Paywall.createRoute(PremiumFeature.PHOTO_VERIFICATION)) },
            )
        }

        composable(
            route = Screen.Paywall.route,
            arguments = listOf(
                navArgument(Screen.ARG_FEATURE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            PaywallScreen(
                onBack = { navController.popBackStack() },
                onPurchased = { navController.popBackStack() },
            )
        }
    }
}
