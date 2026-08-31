package com.habitsfirst.androidclone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-enqueues periodic usage tracking after a reboot (WorkManager itself survives reboot,
 * but re-asserting here is cheap and covers the "first install, never opened" edge case). */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        WorkScheduler.scheduleUsageTracking(context.applicationContext)
    }
}
