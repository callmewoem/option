package com.habitsfirst.androidclone.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitsfirst.androidclone.R
import com.habitsfirst.androidclone.ui.components.CatMood
import com.habitsfirst.androidclone.ui.components.LockeCat

@Composable
fun OnboardingWelcomeScreen(onGetStarted: () -> Unit) {
    // enableEdgeToEdge() (and API 35's enforced edge-to-edge) means this bare Box gets
    // no automatic inset handling the way a Scaffold would -- without this, the
    // bottom-pinned button below renders under the system navigation bar.
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                // Clears the bottom-pinned button below on short screens once this
                // scrolls, rather than letting content land underneath it.
                .padding(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Locke's own mascot greets you first -- a little warmth up front, before
            // the "we're locking your apps" pitch that follows.
            LockeCat(mood = CatMood.Curious, size = 96.dp)
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            // A quick, honest preview of what setup involves and what's waiting on the
            // other side of it -- so "Get Started" isn't a leap into the unknown.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OnboardingFeatureRow(
                    icon = Icons.Filled.Lock,
                    text = stringResource(R.string.onboarding_welcome_feature_lock),
                )
                OnboardingFeatureRow(
                    icon = Icons.Filled.CameraAlt,
                    text = stringResource(R.string.onboarding_welcome_feature_photo),
                )
                OnboardingFeatureRow(
                    icon = Icons.Filled.Redeem,
                    text = stringResource(R.string.onboarding_welcome_feature_rewards),
                )
            }
        }

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(stringResource(R.string.onboarding_get_started), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One row in the welcome screen's "what Locke does" preview -- an icon chip plus its explanation. */
@Composable
private fun OnboardingFeatureRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
        )
    }
}
