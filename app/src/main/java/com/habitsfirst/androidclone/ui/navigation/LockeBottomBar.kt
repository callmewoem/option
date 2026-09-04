package com.habitsfirst.androidclone.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class BottomTab(val screen: Screen, val label: String, val icon: ImageVector)

private val TABS = listOf(
    BottomTab(Screen.Home, "Home", Icons.Filled.Home),
    BottomTab(Screen.Habits, "Stats", Icons.Filled.BarChart),
)

/**
 * Shared bottom nav for the app's top-level destinations. A slightly raised, bordered
 * bar (rather than the bare Material default) so it reads as one more deliberately
 * outlined slab -- consistent with every card in the app -- instead of a plain surface
 * that happens to be pinned to the bottom.
 */
@Composable
fun LockeBottomBar(navController: NavController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.screen.route,
                onClick = {
                    if (currentRoute != tab.screen.route) {
                        navController.navigate(tab.screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
