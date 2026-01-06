package com.e621.client.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Video cache manager - downloads videos to local cache before playback
 * Based on T2's ri.a class
 */
object VideoCache {
    
    private const val TAG = "VideoCache"
    private const val CACHE_DIR = "video_cache"
    private const val BUFFER_SIZE = 8192
    
    /**
     * Callback for download progress
     */
    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long)
        fun onComplete(file: File)
        fun onError(error: String)
    }
    
    /**
     * Get the cache directory for videos
     */
    private fun getCacheDir(context: Context): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }
    
    /**
     * Get cached file for a post ID and extension
     */
    fun getCachedFile(context: Context, postId: Int, extension: String): File {
        return File(getCacheDir(context), "$postId.$extension")
    }
    
    /**
     * Check if video is already cached and valid
     * @param expectedSize Expected file size (0 to skip size check)
     * @param expectedMd5 Expected MD5 hash (null to skip hash check)
     */
    fun isCached(context: Context, postId: Int, extension: String, expectedSize: Long = 0, expectedMd5: String? = null): Boolean {
        val file = getCachedFile(context, postId, extension)
        
        if (!file.exists()) {
            return false
        }
        
        // Check file size if provided
        if (expectedSize > 0 && file.length() != expectedSize) {
            Log.d(TAG, "Cache miss: size mismatch for post $postId (expected $expectedSize, got ${file.length()})")
            file.delete()
            return false
        }
        
        // Check MD5 if provided
        if (!expectedMd5.isNullOrEmpty()) {
            val actualMd5 = calculateMd5(file)
            if (actualMd5 != null && !actualMd5.equals(expectedMd5, ignoreCase = true)) {
                Log.d(TAG, "Cache miss: MD5 mismatch for post $postId")
                file.delete()
                return false
            }
        }
        
        Log.d(TAG, "Cache hit for post $postId")
        return true
    }
    
    /**
     * Download video to cache
     */
    suspend fun downloadToCache(
        context: Context,
        url: String,
        postId: Int,
        extension: String,
        expectedSize: Long = 0,
        callback: DownloadCallback
    ) = withContext(Dispatchers.IO) {
        val file = getCachedFile(context, postId, extension)
        val tempFile = File(getCacheDir(context), "$postId.$extension.tmp")
        
        var connection: HttpURLConnection? = null
        var outputStream: FileOutputStream? = null
        
        try {
            Log.d(TAG, "Starting download for post $postId: $url")
            
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "E621Client/1.0 (Android)")
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                callback.onError("HTTP error: $responseCode")
                return@withContext
            }
            
            val totalBytes = if (expectedSize > 0) expectedSize else connection.contentLength.toLong()
            var bytesDownloaded = 0L
            
            val inputStream = connection.inputStream
            outputStream = FileOutputStream(tempFile)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                
                // Report progress on main thread
                withContext(Dispatchers.Main) {
                    callback.onProgress(bytesDownloaded, totalBytes)
                }
            }
            
            outputStream.flush()
            outputStream.close()
            outputStream = null
            
            // Rename temp file to final file
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)
            
            Log.d(TAG, "Download complete for post $postId: ${file.length()} bytes")
            
            withContext(Dispatchers.Main) {
                callback.onComplete(file)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for post $postId", e)
            tempFile.delete()
            
            withContext(Dispatchers.Main) {
                callback.onError(e.message ?: "Download failed")
            }
        } finally {
            try {
                outputStream?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Calculate MD5 hash of a file
     */
    private fun calculateMd5(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val inputStream = FileInputStream(file)
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            
            String.format("%032x", BigInteger(1, digest.digest()))
        } catch (e: Exception) {
            Log.e(TAG, "MD5 calculation failed", e)
            null
        }
    }
    
    /**
     * Get total cache size in bytes
     */
    fun getCacheSize(context: Context): Long {
        return calculateDirSize(getCacheDir(context))
    }
    
    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }
    
    /**
     * Clear all cached videos
     */
    fun clearCache(context: Context) {
        val cacheDir = getCacheDir(context)
        cacheDir.listFiles()?.forEach { file ->
            file.delete()
        }
        Log.d(TAG, "Cache cleared")
    }
    
    /**
     * Delete old cache files to free space
     * Keeps most recent files up to maxSize
     */
    fun trimCache(context: Context, maxSize: Long) {
        val cacheDir = getCacheDir(context)
        val files = cacheDir.listFiles()?.filter { !it.name.endsWith(".tmp") }?.toMutableList() ?: return
        
        // Sort by last modified (oldest first)
        files.sortBy { it.lastModified() }
        
        var currentSize = files.sumOf { it.length() }
        
        // Delete oldest files until under maxSize
        while (currentSize > maxSize && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            currentSize -= oldest.length()
            oldest.delete()
            Log.d(TAG, "Trimmed cache: deleted ${oldest.name}")
        }
    }
}
