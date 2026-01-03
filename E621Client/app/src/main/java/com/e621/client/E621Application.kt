package com.e621.client

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.e621.client.data.api.E621Api
import com.e621.client.data.preferences.UserPreferences
import com.e621.client.service.TagMonitoringService
import com.e621.client.worker.FollowedTagsWorker
import com.e621.client.worker.UpdateCheckWorker
import java.util.concurrent.TimeUnit

/**
 * Application class for E621 Client
 * Initializes core components like API client and preferences
 */
class E621Application : Application() {

    lateinit var userPreferences: UserPreferences
        private set
    
    // Alias for easier access
    val prefs: UserPreferences get() = userPreferences
    
    lateinit var api: E621Api
        private set

    // Global scope for background operations that should survive activity lifecycle
    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize preferences
        userPreferences = UserPreferences(this)
        
        // Apply theme globally at app startup
        applyTheme()
        
        // Initialize API
        api = E621Api.create(userPreferences)
        
        // Setup followed tags monitoring (Worker or Service)
        setupTagMonitoring()
        
        // Setup automatic update checking (daily)
        setupUpdateChecker()
        
        // Check cache size and clear if needed
        checkAndClearCache()
    }
    
    /**
     * Apply theme based on user preferences
     */
    private fun applyTheme() {
        when (userPreferences.theme) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    /**
     * Configures the background monitoring for followed tags (Worker or Service)
     */
    private fun setupTagMonitoring() {
        val workManager = WorkManager.getInstance(this)
        val serviceIntent = Intent(this, TagMonitoringService::class.java)

        if (!userPreferences.followedTagsNotificationsEnabled) {
            // Cancel everything if disabled
            workManager.cancelUniqueWork(FollowedTagsWorker.WORK_NAME)
            stopService(serviceIntent)
            return
        }

        // Interval is stored in minutes
        val intervalMinutes = userPreferences.followedTagsCheckInterval.toLong()

        if (intervalMinutes == 1L) {
            // INSTANT MODE: Use Foreground Service
            // 1. Cancel Worker
            workManager.cancelUniqueWork(FollowedTagsWorker.WORK_NAME)
            
            // 2. Start Service
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            // PERIODIC MODE: Use WorkManager
            // 1. Stop Service
            stopService(serviceIntent)

            // 2. Schedule Worker
            // Set network constraint based on onlyWifi preference
            val networkType = if (userPreferences.followingOnlyWifi) {
                NetworkType.UNMETERED // WiFi only
            } else {
                NetworkType.CONNECTED // Any network
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            
            // Ensure minimum 15 minutes for WorkManager
            val safeInterval = intervalMinutes.coerceAtLeast(15)
            
            val workRequest = PeriodicWorkRequestBuilder<FollowedTagsWorker>(
                safeInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                FollowedTagsWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
    
    /**
     * Call this method to reschedule the worker when settings change
     */
    fun rescheduleFollowedTagsWorker() {
        setupTagMonitoring()
    }
    
    /**
     * Recreate the API client when host changes
     * This is necessary because Retrofit's baseUrl is set at creation time
     */
    fun recreateApi() {
        api = E621Api.create(userPreferences)
    }
    
    /**
     * Check cache size and clear if it exceeds the limit
     */
    private fun checkAndClearCache() {
        if (!userPreferences.storageMaxCache) return
        
        val maxCacheMB = userPreferences.storageMaxCacheSize
        val maxCacheBytes = maxCacheMB.toLong() * 1024 * 1024
        
        Thread {
            try {
                var currentCacheSize = 0L
                
                // App cache directory
                cacheDir?.let { dir ->
                    currentCacheSize += calculateDirSize(dir)
                }
                
                // External cache directory
                externalCacheDir?.let { dir ->
                    currentCacheSize += calculateDirSize(dir)
                }
                
                if (currentCacheSize > maxCacheBytes) {
                    android.util.Log.d("E621Cache", "Cache size ($currentCacheSize bytes) exceeds limit ($maxCacheBytes bytes). Clearing...")
                    clearAppCache()
                }
            } catch (e: Exception) {
                android.util.Log.e("E621Cache", "Error checking cache: ${e.message}", e)
            }
        }.start()
    }
    
    /**
     * Calculate directory size recursively
     */
    private fun calculateDirSize(dir: java.io.File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }
    
    /**
     * Clear all app cache
     */
    private fun clearAppCache() {
        try {
            cacheDir?.deleteRecursively()
            externalCacheDir?.deleteRecursively()
            
            // Clear Glide cache
            try {
                com.bumptech.glide.Glide.get(this).clearDiskCache()
            } catch (e: Exception) {
                android.util.Log.e("E621Cache", "Error clearing Glide cache: ${e.message}", e)
            }
            
            android.util.Log.d("E621Cache", "Cache cleared successfully")
        } catch (e: Exception) {
            android.util.Log.e("E621Cache", "Error clearing cache: ${e.message}", e)
        }
    }
    
    /**
     * Setup periodic update checker (runs daily)
     */
    private fun setupUpdateChecker() {
        val workManager = WorkManager.getInstance(this)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Check for updates once per day (24 hours)
        val updateWorkRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing, don't replace
            updateWorkRequest
        )
        
        android.util.Log.d("E621App", "Update checker scheduled (daily)")
    }

    companion object {
        lateinit var instance: E621Application
            private set
    }
}
