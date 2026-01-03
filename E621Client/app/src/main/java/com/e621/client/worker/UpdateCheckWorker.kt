package com.e621.client.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.ui.MainActivity
import com.e621.client.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker that periodically checks for app updates
 * and shows a notification when a new version is available.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "update_check_worker"
        private const val CHANNEL_ID = "update_channel"
        private const val NOTIFICATION_ID = 9999
        private const val TAG = "UpdateCheckWorker"
        
        // Preference key for last check timestamp
        const val PREF_LAST_UPDATE_CHECK = "last_update_check_timestamp"
    }

    private val updateChecker by lazy { UpdateChecker(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting update check...")
            
            // Check for updates
            val result = updateChecker.checkForUpdate()
            
            result.onSuccess { updateInfo ->
                if (updateInfo != null && updateInfo.isNewer) {
                    Log.d(TAG, "Update available: ${updateInfo.versionName}")
                    // Show notification
                    showUpdateNotification(updateInfo)
                } else {
                    Log.d(TAG, "No update available")
                }
                
                // Save last check timestamp
                saveLastCheckTimestamp()
            }.onFailure { error ->
                Log.e(TAG, "Error checking for updates", error)
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in doWork", e)
            Result.retry()
        }
    }

    /**
     * Save current timestamp as last check time
     */
    private fun saveLastCheckTimestamp() {
        val prefs = applicationContext.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(PREF_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

    /**
     * Create notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Updates"
            val descriptionText = "Notifications for app updates"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show notification for available update
     */
    private fun showUpdateNotification(updateInfo: UpdateChecker.UpdateInfo) {
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted")
                return
            }
        }

        createNotificationChannel()

        // Create intent to open app and trigger update
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("check_update", true)
            putExtra("update_version", updateInfo.versionName)
            putExtra("update_url", updateInfo.downloadUrl)
            putExtra("update_changelog", updateInfo.changelog)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentVersion = updateChecker.getCurrentVersion()
        
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Update Available")
            .setContentText("Version ${updateInfo.versionName} is available (current: $currentVersion)")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Version ${updateInfo.versionName} is available.\nCurrent version: $currentVersion\n\nTap to download and install."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        
        Log.d(TAG, "Update notification shown")
    }
}
