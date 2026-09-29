package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.preference.UserPreferencesRepository
import com.example.service.FileProcessingService
import com.example.service.WifiMonitoringService

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                val prefs = UserPreferencesRepository(context)
                prefs.setBackgroundMonitoring(false)
                WifiMonitoringService.stop(context)
            }
            ACTION_PAUSE_IMPORT -> {
                FileProcessingService.pauseImport(context)
            }
            ACTION_RESUME_IMPORT -> {
                val prefs = UserPreferencesRepository(context)
                val checkpoint = prefs.getImportCheckpoint()
                if (checkpoint != null && checkpoint.uriString.isNotBlank()) {
                    FileProcessingService.resumeImport(
                        context = context,
                        uri = android.net.Uri.parse(checkpoint.uriString),
                        fileName = checkpoint.fileName.ifBlank { "passwords.txt" }
                    )
                }
            }
            ACTION_STOP_IMPORT -> {
                FileProcessingService.stopImport(context)
            }
        }
    }

    companion object {
        const val ACTION_STOP_MONITORING = "com.example.receiver.ACTION_STOP_MONITORING"
        const val ACTION_PAUSE_IMPORT = "com.example.receiver.ACTION_PAUSE_IMPORT"
        const val ACTION_RESUME_IMPORT = "com.example.receiver.ACTION_RESUME_IMPORT"
        const val ACTION_STOP_IMPORT = "com.example.receiver.ACTION_STOP_IMPORT"
    }
}
