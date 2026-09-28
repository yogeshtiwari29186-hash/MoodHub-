package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.preference.UserPreferencesRepository
import com.example.service.WifiMonitoringService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = UserPreferencesRepository(context)
            // Resume only if user explicitly enabled both background monitoring AND resume on boot
            if (prefs.isBackgroundMonitoringEnabled() && prefs.resumeOnBoot.value) {
                WifiMonitoringService.start(context)
            }
        }
    }
}
