package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.preference.UserPreferencesRepository
import com.example.service.WifiMonitoringService

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_MONITORING) {
            val prefs = UserPreferencesRepository(context)
            prefs.setBackgroundMonitoring(false)
            WifiMonitoringService.stop(context)
        }
    }

    companion object {
        const val ACTION_STOP_MONITORING = "com.example.receiver.ACTION_STOP_MONITORING"
    }
}
