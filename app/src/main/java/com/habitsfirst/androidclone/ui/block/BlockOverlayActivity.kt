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
 * Full-screen "this is locked" cover shown on top of a blocked app, or a browser
 * showing a blocked site. Launched by
 * [com.habitsfirst.androidclone.service.AppBlockAccessibilityService] the instant the
 * user switches into a locked app, or navigates to a blocked host, that isn't
 * currently allowed to be open.
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
        /** The blocked app's package name, or the blocked host -- see [EXTRA_IS_URL_BLOCK]. */
        const val EXTRA_TARGET = "extra_target"
        const val EXTRA_IS_URL_BLOCK = "extra_is_url_block"
        /** URL block only: which list matched. */
        const val EXTRA_LIST_NAME = "extra_list_name"
        /** URL block only: true when the matching list is [com.habitsfirst.androidclone.domain.model.BlockMode.PERMANENT]. */
        const val EXTRA_IS_PERMANENT = "extra_is_permanent"
        const val EXTRA_IS_BEDTIME = "extra_is_bedtime"
        /** True when today's gating habits are already complete but this is locked anyway (an active penalty, or limited unblocking's window running out) -- see [com.habitsfirst.androidclone.service.AppBlockAccessibilityService]. */
        const val EXTRA_HABITS_COMPLETE_BUT_LOCKED = "extra_habits_complete_but_locked"
    }
}
