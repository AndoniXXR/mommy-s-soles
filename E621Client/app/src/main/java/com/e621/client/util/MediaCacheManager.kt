package com.e621.client.util

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton manager for ExoPlayer media cache
 * Based on T2's implementation using CacheDataSource for streaming with progressive cache
 * 
 * This provides a global cache that persists across Activities, preventing resource exhaustion
 * when navigating between parent/child posts with videos.
 */
@UnstableApi
object MediaCacheManager {
    
    private const val CACHE_DIR = "media_cache"
    private const val MAX_CACHE_SIZE = 100L * 1024 * 1024 // 100MB like T2
    private const val USER_AGENT = "E621Client/1.0 (Android; by e621_client)"
    
    @Volatile
    private var cache: Cache? = null
    private val cacheLock = Any()
    
    /**
     * Get or create the global media cache
     * Thread-safe singleton initialization
     */
    fun getCache(context: Context): Cache {
        return cache ?: synchronized(cacheLock) {
            cache ?: createCache(context).also { cache = it }
        }
    }
    
    private fun createCache(context: Context): Cache {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
        val databaseProvider = StandaloneDatabaseProvider(context)
        
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }
    
    /**
     * Create a CacheDataSource.Factory for use with ExoPlayer
     * This enables streaming with progressive caching - video plays while downloading
     * Timeouts are adjusted based on network quality
     */
    fun createCacheDataSourceFactory(context: Context): DataSource.Factory {
        val cache = getCache(context)
        
        // Adjust timeouts based on network quality
        val networkQuality = NetworkMonitor.getConnectionQuality()
        val (connectTimeout, readTimeout) = when (networkQuality) {
            NetworkMonitor.ConnectionQuality.EXCELLENT -> 15_000 to 20_000
            NetworkMonitor.ConnectionQuality.GOOD -> 20_000 to 30_000
            NetworkMonitor.ConnectionQuality.MODERATE -> 25_000 to 40_000
            NetworkMonitor.ConnectionQuality.POOR -> 30_000 to 60_000
            NetworkMonitor.ConnectionQuality.UNKNOWN -> {
                // If unknown, check if metered (mobile data)
                if (NetworkMonitor.isMetered()) 25_000 to 40_000 else 15_000 to 20_000
            }
        }
        
        // Upstream data source for network requests
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(connectTimeout)
            .setReadTimeoutMs(readTimeout)
            .setAllowCrossProtocolRedirects(true)
        
        // CacheDataSource that reads from cache first, then network
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    
    /**
     * Release the cache when app is being destroyed
     * Should be called from Application.onTerminate() or similar
     */
    fun release() {
        synchronized(cacheLock) {
            cache?.release()
            cache = null
        }
    }
    
    /**
     * Get current cache size in bytes
     */
    fun getCacheSize(context: Context): Long {
        return getCache(context).cacheSpace
    }
    
    /**
     * Clear all cached media
     */
    fun clearCache(context: Context) {
        synchronized(cacheLock) {
            cache?.release()
            cache = null
            
            // Delete cache directory
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
    }
}
