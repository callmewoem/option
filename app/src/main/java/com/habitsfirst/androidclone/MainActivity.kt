package com.habitsfirst.androidclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.habitsfirst.androidclone.ui.navigation.HabitsFirstNavHost
import com.habitsfirst.androidclone.ui.theme.LockeMode
import com.habitsfirst.androidclone.ui.theme.LockeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LockeRoot()
        }
    }
}

/**
 * App mode at the root -- individual screens (the block cover, curfew, the morning
 * lock) switch to [LockeMode.Enforcement] themselves via their own [LockeTheme] call,
 * same pattern as [com.habitsfirst.androidclone.ui.block.BlockOverlayActivity].
 */
@Composable
private fun LockeRoot() {
    LockeTheme(mode = LockeMode.App) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HabitsFirstNavHost()
        }
    }
}
