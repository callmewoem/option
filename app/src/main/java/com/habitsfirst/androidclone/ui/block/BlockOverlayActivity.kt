package com.habitsfirst.androidclone.ui.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitsfirst.androidclone.MainActivity
import com.habitsfirst.androidclone.ui.theme.AppThemeViewModel
import com.habitsfirst.androidclone.ui.theme.LockeTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen "this app is locked" cover shown on top of a blocked app. Launched by
 * [com.habitsfirst.androidclone.service.AppBlockAccessibilityService] the instant the
 * user switches into a locked app with unfinished habits.
 */
@AndroidEntryPoint
class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block the hardware back button from simply revealing the locked app underneath.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goHome()
                }
            },
        )

        setContent {
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val variant by themeViewModel.selectedVariant.collectAsStateWithLifecycle()
            LockeTheme(darkTheme = true, variant = variant) {
                BlockScreen(
                    onTakeBreak = ::goHome,
                    onOpenHabitsFirst = ::openHabitsFirst,
                    onAllHabitsComplete = ::finish,
                    onGraceRedeemed = ::finish,
                )
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        finish()
    }

    private fun openHabitsFirst() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_IS_BEDTIME = "extra_is_bedtime"
    }
}
