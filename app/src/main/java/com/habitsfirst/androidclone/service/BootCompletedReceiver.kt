package com.habitsfirst.androidclone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitsfirst.androidclone.data.repository.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Re-enqueues periodic usage tracking after a reboot (WorkManager itself survives reboot,
 * but re-asserting here is cheap and covers the "first install, never opened" edge case). */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesRepository: PreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        WorkScheduler.scheduleUsageTracking(appContext)
        WorkScheduler.scheduleMorningTodoReminder(appContext)
        WorkScheduler.scheduleProofOfLifeCheck(appContext)
        WorkScheduler.scheduleBlocklistRefresh(appContext)

        // Health Connect sync and the weekly digest are both opt-in (unlike the four
        // above), so only re-assert them if the user had actually turned them on before
        // the reboot -- goAsync() keeps the receiver alive long enough for these
        // one-shot DataStore reads to finish.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            if (preferencesRepository.isHealthConnectSyncEnabled.first()) {
                WorkScheduler.scheduleHealthConnectSync(appContext)
            }
            if (preferencesRepository.weeklyDigestSettings.first().enabled) {
                WorkScheduler.scheduleWeeklyDigest(appContext)
            }
            pendingResult.finish()
        }
    }
}
