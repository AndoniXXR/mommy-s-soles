package com.e621.client.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.e621.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * UpdateChecker - Handles checking for app updates and downloading/installing them
 * 
 * This checks a GitHub releases API endpoint for new versions and can download
 * and install APK updates automatically.
 */
class UpdateChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateChecker"
        
        // GitHub API endpoint for releases
        private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/AndoniXXR/mommy-s-soles/releases/latest"
        
        // Alternative: Direct URL to a version.json file hosted somewhere
        private const val VERSION_CHECK_URL = "https://raw.githubusercontent.com/AndoniXXR/mommy-s-soles/main/version.json"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val changelog: String = "",
        val isNewer: Boolean = false
    )
    
    /**
     * Check for updates from GitHub releases
     * Returns UpdateInfo if an update is available, null otherwise
     */
    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            // Try GitHub releases first
            val result = checkGitHubReleases()
            if (result.isSuccess) {
                return@withContext result
            }
            
            // Fallback to version.json
            checkVersionJson()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            Result.failure(e)
        }
    }
    
    private fun checkGitHubReleases(): Result<UpdateInfo?> {
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API returned ${response.code}")
                return Result.failure(Exception("GitHub API error: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = JSONObject(body)
            
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val changelog = json.optString("body", "")
            
            // Find APK asset
            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }
            
            if (tagName.isEmpty() || downloadUrl.isEmpty()) {
                return Result.success(null)
            }
            
            val isNewer = isVersionNewer(tagName, BuildConfig.VERSION_NAME)
            
            return Result.success(UpdateInfo(
                versionName = tagName,
                versionCode = parseVersionCode(tagName),
                downloadUrl = downloadUrl,
                changelog = changelog,
                isNewer = isNewer
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking GitHub releases", e)
            return Result.failure(e)
        }
    }
    
    private fun checkVersionJson(): Result<UpdateInfo?> {
        try {
            val request = Request.Builder()
                .url(VERSION_CHECK_URL)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("Version check failed: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = JSONObject(body)
            
            val versionName = json.optString("version_name", "")
            val versionCode = json.optInt("version_code", 0)
            val downloadUrl = json.optString("download_url", "")
            val changelog = json.optString("changelog", "")
            
            if (versionName.isEmpty() || downloadUrl.isEmpty()) {
                return Result.success(null)
            }
            
            val isNewer = versionCode > BuildConfig.VERSION_CODE || 
                         isVersionNewer(versionName, BuildConfig.VERSION_NAME)
            
            return Result.success(UpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                downloadUrl = downloadUrl,
                changelog = changelog,
                isNewer = isNewer
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking version.json", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Compare two version strings (e.g., "1.2.3" vs "1.2.4")
     * Returns true if newVersion > currentVersion
     */
    private fun isVersionNewer(newVersion: String, currentVersion: String): Boolean {
        try {
            val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLength = maxOf(newParts.size, currentParts.size)
            
            for (i in 0 until maxLength) {
                val newPart = newParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }
                
                if (newPart > currentPart) return true
                if (newPart < currentPart) return false
            }
            
            return false // Same version
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
            return false
        }
    }
    
    private fun parseVersionCode(versionName: String): Int {
        try {
            val parts = versionName.split(".")
            if (parts.size >= 3) {
                val major = parts[0].toIntOrNull() ?: 0
                val minor = parts[1].toIntOrNull() ?: 0
                val patch = parts[2].toIntOrNull() ?: 0
                return major * 10000 + minor * 100 + patch
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing version code", e)
        }
        return 0
    }
    
    /**
     * Download APK using DownloadManager
     * Returns download ID
     */
    fun downloadUpdate(updateInfo: UpdateInfo, onProgress: (Int) -> Unit = {}, onComplete: (File?) -> Unit): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val fileName = "app-update-${updateInfo.versionName}.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        // Delete old file if exists
        if (destinationFile.exists()) {
            destinationFile.delete()
        }
        
        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            .setTitle("Downloading Update")
            .setDescription("Downloading version ${updateInfo.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloadId = downloadManager.enqueue(request)
        
        // Register receiver for download complete
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            onComplete(destinationFile)
                        } else {
                            onComplete(null)
                        }
                    }
                    cursor.close()
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
        
        return downloadId
    }
    
    /**
     * Install APK file
     */
    fun installApk(file: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
        }
    }
    
    /**
     * Get current app version
     */
    fun getCurrentVersion(): String = BuildConfig.VERSION_NAME
    
    fun getCurrentVersionCode(): Int = BuildConfig.VERSION_CODE
}
