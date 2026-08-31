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
import com.habitsfirst.androidclone.ui.theme.HabitsFirstTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabitsFirstRoot()
        }
    }
}

@Composable
private fun HabitsFirstRoot() {
    HabitsFirstTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            HabitsFirstNavHost()
        }
    }
}
