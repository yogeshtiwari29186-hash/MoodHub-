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

            // Safely mark any in-progress file import as PAUSED on system reboot
            val checkpoint = prefs.getImportCheckpoint()
            if (checkpoint != null && checkpoint.status == "INDEXING") {
                prefs.updateCheckpointStatus("PAUSED")
            }

            // Resume background Wi-Fi monitoring only if user explicitly enabled both
            if (prefs.isBackgroundMonitoringEnabled() && prefs.resumeOnBoot.value) {
                WifiMonitoringService.start(context)
            }
        }
    }
}
