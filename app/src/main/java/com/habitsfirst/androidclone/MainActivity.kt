package com.habitsfirst.androidclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.ui.navigation.HabitsFirstNavHost
import com.habitsfirst.androidclone.ui.theme.AppThemeViewModel
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

@Composable
private fun LockeRoot(themeViewModel: AppThemeViewModel = hiltViewModel()) {
    val variant by themeViewModel.selectedVariant.collectAsStateWithLifecycle()
    LockeTheme(variant = variant) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HabitsFirstNavHost()
        }
    }
}
