package com.e621.client

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.e621.client.data.api.E621Api
import com.e621.client.data.preferences.UserPreferences
import com.e621.client.worker.FollowedTagsWorker
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize preferences
        userPreferences = UserPreferences(this)
        
        // Apply theme globally at app startup
        applyTheme()
        
        // Initialize API
        api = E621Api.create(userPreferences)
        
        // Setup followed tags worker
        setupFollowedTagsWorker()
        
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
     * Configures the periodic work for checking followed tags for new posts
     */
    private fun setupFollowedTagsWorker() {
        if (!userPreferences.followedTagsNotificationsEnabled) {
            // Cancel any existing work if notifications are disabled
            WorkManager.getInstance(this).cancelUniqueWork(FollowedTagsWorker.WORK_NAME)
            return
        }
        
        // Set network constraint based on onlyWifi preference
        val networkType = if (userPreferences.followingOnlyWifi) {
            NetworkType.UNMETERED // WiFi only
        } else {
            NetworkType.CONNECTED // Any network
        }
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()
        
        // Interval is stored in minutes
        val intervalMinutes = userPreferences.followedTagsCheckInterval.toLong().coerceAtLeast(15)
        
        val workRequest = PeriodicWorkRequestBuilder<FollowedTagsWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            FollowedTagsWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
    
    /**
     * Call this method to reschedule the worker when settings change
     */
    fun rescheduleFollowedTagsWorker() {
        setupFollowedTagsWorker()
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

    companion object {
        lateinit var instance: E621Application
            private set
    }
}
