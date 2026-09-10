package com.locke.app

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
import com.locke.app.ui.navigation.LockeNavHost
import com.locke.app.ui.theme.LockeMode
import com.locke.app.ui.theme.LockeTheme
import com.locke.app.ui.todo.OverdueTodoDialog
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
 * same pattern as [com.locke.app.ui.block.BlockOverlayActivity].
 *
 * Requesting [AppViewModel] here rather than from inside [LockeNavHost] gives it
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

    LockeTheme(mode = LockeMode.App) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LockeNavHost()
        }
    }
}
