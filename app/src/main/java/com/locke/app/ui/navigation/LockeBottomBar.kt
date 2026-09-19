package com.locke.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.locke.app.R
import com.locke.app.ui.theme.LockeColor
import com.locke.app.ui.theme.SpaceGrotesk

private data class BottomTab(val screen: Screen, val label: String, val iconRes: Int)

private val TABS = listOf(
    BottomTab(Screen.Home, "Today", R.drawable.ic_nav_sun),
    BottomTab(Screen.Todo, "To-do", R.drawable.ic_nav_checklist),
    BottomTab(Screen.Habits, "Stats", R.drawable.ic_nav_bar_chart),
)

/**
 * Today / To-do / Stats, icon over label -- design spec §3: "active tab has a soft moss
 * pill behind the icon." App mode only, never shown in enforcement mode.
 */
@Composable
fun LockeBottomBar(navController: NavController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LockeColor.Bone)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
    ) {
        TABS.forEach { tab ->
            val selected = currentRoute == tab.screen.route
            Column(
                modifier = Modifier
                    .clickable {
                        if (currentRoute != tab.screen.route) {
                            navController.navigate(tab.screen.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .then(
                            if (selected) {
                                Modifier.background(LockeColor.Moss.copy(alpha = 0.14f), RoundedCornerShape(50))
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = tab.label,
                        tint = if (selected) LockeColor.Moss else LockeColor.MutedText,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                    ),
                    color = if (selected) LockeColor.Moss else LockeColor.MutedText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
