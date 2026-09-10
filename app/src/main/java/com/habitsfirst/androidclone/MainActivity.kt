package com.habitsfirst.androidclone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.ui.navigation.HabitsFirstNavHost
import com.habitsfirst.androidclone.ui.theme.LockeMode
import com.habitsfirst.androidclone.ui.theme.LockeTheme
import com.habitsfirst.androidclone.ui.todo.OverdueTodoDialog
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
 *
 * Requesting [AppViewModel] here rather than from inside [HabitsFirstNavHost] gives it
 * this Activity's [androidx.lifecycle.ViewModelStoreOwner] instead of one scoped to a
 * single nav destination, so it survives switching tabs and lives exactly as long as
 * the app does -- which is what makes its [AppViewModel.onAppResumed] cold-launch/resume
 * check meaningful in the first place.
 */
@Composable
private fun LockeRoot() {
    val appViewModel: AppViewModel = hiltViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) appViewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overduePrompt by appViewModel.overduePrompt.collectAsStateWithLifecycle()
    overduePrompt?.let { prompt ->
        OverdueTodoDialog(
            prompt = prompt,
            onToggle = appViewModel::onToggleOverdueSelection,
            onConfirm = appViewModel::onConfirmOverduePrompt,
            onDismiss = appViewModel::onDismissOverduePrompt,
        )
    }

    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
    LockeTheme(mode = LockeMode.App, themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HabitsFirstNavHost()
        }
    }
}
