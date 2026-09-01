package com.habitsfirst.androidclone.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Health Connect's own permission-grant screen and its Settings ▸ App permissions page
 * both link out to a rationale for the health data an app reads -- Health Connect
 * refuses to grant permissions at all without one declared (see the
 * `PermissionsRationaleActivity` / `ViewPermissionUsageActivity` entries in
 * AndroidManifest.xml). This is that rationale: a standalone screen (no Hilt, no app
 * navigation state) since it can be launched by Health Connect itself, outside
 * [com.habitsfirst.androidclone.MainActivity]'s normal flow.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Health Connect permissions", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Locke reads your step count from Health Connect, read-only, only to " +
                                "fill in progress on a Steps habit you've created -- so you don't have " +
                                "to log it by hand. Nothing is written back to Health Connect, and " +
                                "nothing leaves your device.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = ::finish) { Text("Close") }
                    }
                }
            }
        }
    }
}
