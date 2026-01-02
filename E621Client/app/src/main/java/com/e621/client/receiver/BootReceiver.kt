package com.e621.client.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.e621.client.data.preferences.UserPreferences
import com.e621.client.service.TagMonitoringService

/**
 * BootReceiver to restart the monitoring service after device reboot
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = UserPreferences(context)
            
            // Check if notifications are enabled and interval is set to Instant (1 minute)
            if (prefs.followedTagsNotificationsEnabled && prefs.followedTagsCheckInterval == 1) {
                TagMonitoringService.start(context)
            }
        }
    }
}