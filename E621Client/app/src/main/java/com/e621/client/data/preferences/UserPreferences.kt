package com.e621.client.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages user preferences and credentials
 * Based on decompiled LoginActivity patterns
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Username
    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit { putString(KEY_USERNAME, value) }
    
    // API Key
    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit { putString(KEY_API_KEY, value) }
    
    // Safe Mode (e926 vs e621)
    var safeMode: Boolean
        get() = prefs.getBoolean(KEY_SAFE_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_SAFE_MODE, value) }
    
    // Theme (0=system, 1=light, 2=dark)
    var theme: Int
        get() = prefs.getInt(KEY_THEME, 0)
        set(value) = prefs.edit { putInt(KEY_THEME, value) }
    
    // Image quality (0=low, 1=medium, 2=high)
    var imageQuality: Int
        get() = prefs.getInt(KEY_IMAGE_QUALITY, 1)
        set(value) = prefs.edit { putInt(KEY_IMAGE_QUALITY, value) }
    
    // Grid columns
    var gridColumns: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, 3)
        set(value) = prefs.edit { putInt(KEY_GRID_COLUMNS, value) }
    
    // Blacklist
    var blacklist: String?
        get() = prefs.getString(KEY_BLACKLIST, null)
        set(value) = prefs.edit { putString(KEY_BLACKLIST, value) }
    
    // Saved Searches (stored as comma-separated list)
    var savedSearches: Set<String>
        get() = prefs.getStringSet(KEY_SAVED_SEARCHES, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_SAVED_SEARCHES, value) }
    
    // Followed Tags (stored as set)
    var followedTags: Set<String>
        get() = prefs.getStringSet(KEY_FOLLOWED_TAGS, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_FOLLOWED_TAGS, value) }
    
    /**
     * Add a tag to saved searches
     */
    fun addSavedSearch(tag: String) {
        val current = savedSearches.toMutableSet()
        current.add(tag)
        savedSearches = current
    }
    
    /**
     * Remove a tag from saved searches
     */
    fun removeSavedSearch(tag: String) {
        val current = savedSearches.toMutableSet()
        current.remove(tag)
        savedSearches = current
    }
    
    /**
     * Check if a search is saved
     */
    fun isSavedSearch(tag: String): Boolean = savedSearches.contains(tag)
    
    /**
     * Clear all saved searches
     */
    fun clearSavedSearches() {
        savedSearches = emptySet()
    }
    
    /**
     * Add a tag to blacklist
     */
    fun addToBlacklist(tag: String) {
        val current = blacklist?.split("\n")?.toMutableList() ?: mutableListOf()
        if (!current.contains(tag)) {
            current.add(tag)
            blacklist = current.joinToString("\n")
        }
    }
    
    /**
     * Remove a tag from blacklist
     */
    fun removeFromBlacklist(tag: String) {
        val current = blacklist?.split("\n")?.toMutableList() ?: mutableListOf()
        current.remove(tag)
        blacklist = current.joinToString("\n")
    }
    
    /**
     * Check if a tag is blacklisted
     */
    fun isBlacklisted(tag: String): Boolean {
        return blacklist?.split("\n")?.contains(tag) == true
    }
    
    /**
     * Follow a tag
     */
    fun followTag(tag: String) {
        val current = followedTags.toMutableSet()
        current.add(tag)
        followedTags = current
    }
    
    /**
     * Unfollow a tag
     */
    fun unfollowTag(tag: String) {
        val current = followedTags.toMutableSet()
        current.remove(tag)
        followedTags = current
    }
    
    /**
     * Check if a tag is followed
     */
    fun isFollowingTag(tag: String): Boolean = followedTags.contains(tag)
    
    // ==================== Followed Tags Tracking ====================
    
    /**
     * Get last seen post IDs for all followed tags
     */
    fun getLastSeenPostIds(): Map<String, Int> {
        val json = prefs.getString(KEY_LAST_SEEN_POST_IDS, null) ?: return emptyMap()
        return try {
            val map = mutableMapOf<String, Int>()
            json.split(";").forEach { pair ->
                val parts = pair.split("=")
                if (parts.size == 2) {
                    map[parts[0]] = parts[1].toIntOrNull() ?: 0
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Save last seen post ID for a tag
     */
    fun saveLastSeenPostId(tag: String, postId: Int) {
        val current = getLastSeenPostIds().toMutableMap()
        current[tag] = postId
        val json = current.entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs.edit { putString(KEY_LAST_SEEN_POST_IDS, json) }
    }
    
    /**
     * Clear last seen post ID when unfollowing
     */
    fun clearLastSeenPostId(tag: String) {
        val current = getLastSeenPostIds().toMutableMap()
        current.remove(tag)
        val json = current.entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs.edit { putString(KEY_LAST_SEEN_POST_IDS, json) }
    }
    
    // ==================== Followed Tags Check Settings ====================
    
    // Enable/disable followed tags notifications
    var followedTagsNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOLLOWED_TAGS_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_FOLLOWED_TAGS_NOTIFICATIONS, value) }
    
    // Check interval in minutes (1, 15, 30, 60, 120, 360, 720, 1440)
    var followedTagsCheckInterval: Int
        get() = prefs.getInt(KEY_FOLLOWED_TAGS_INTERVAL, 1)
        set(value) = prefs.edit { putInt(KEY_FOLLOWED_TAGS_INTERVAL, value) }
    
    // Only check on WiFi/unmetered networks
    var followingOnlyWifi: Boolean
        get() = prefs.getBoolean(KEY_FOLLOWING_ONLY_WIFI, false)
        set(value) = prefs.edit { putBoolean(KEY_FOLLOWING_ONLY_WIFI, value) }
    
    // Display tag name in notification
    var followingDisplayTag: Boolean
        get() = prefs.getBoolean(KEY_FOLLOWING_DISPLAY_TAG, true)
        set(value) = prefs.edit { putBoolean(KEY_FOLLOWING_DISPLAY_TAG, value) }
    
    // Display followed indicator in saved searches
    var followingDisplayInSavedSearch: Boolean
        get() = prefs.getBoolean(KEY_FOLLOWING_DISPLAY_IN_SAVED_SEARCH, true)
        set(value) = prefs.edit { putBoolean(KEY_FOLLOWING_DISPLAY_IN_SAVED_SEARCH, value) }
    
    // Following tags as string (for editing)
    var followingTags: String
        get() = followedTags.joinToString("\n")
        set(value) {
            val tags = value.split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            followedTags = tags
        }
    
    // Last search
    var lastSearch: String?
        get() = prefs.getString(KEY_LAST_SEARCH, null)
        set(value) = prefs.edit { putString(KEY_LAST_SEARCH, value) }
    
    // ==================== Search Preferences ====================
    
    // Search history enabled (if disabled, clears history)
    var searchHistoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_HISTORY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_HISTORY_ENABLED, value) }
    
    // Search suggestions while typing
    var searchSuggestions: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_SUGGESTIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_SUGGESTIONS, value) }
    
    // Open saved search in new window when pressing Continue
    var searchSavedNewWindow: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_SAVED_NEW_WINDOW, true)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_SAVED_NEW_WINDOW, value) }
    
    // Load last search when app starts
    var searchLastOnStart: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_LAST_ON_START, true)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_LAST_ON_START, value) }
    
    // Open tags in a new task in task switcher
    var searchInNewTask: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_IN_NEW_TASK, true)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_IN_NEW_TASK, value) }
    
    // Sort favorites by date favorited (instead of filter order)
    var searchFavOrder: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_FAV_ORDER, true)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_FAV_ORDER, value) }
    
    // Show newest first when searching for tags (overrides selected order)
    var searchNewestFirst: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_NEWEST_FIRST, false)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_NEWEST_FIRST, value) }
    
    // Include flash posts in search results
    var searchIncludeFlash: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_INCLUDE_FLASH, false)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_INCLUDE_FLASH, value) }
    
    // ==================== Grid Preferences ====================
    
    // Show stats (score/favs) on thumbnails
    var gridStats: Boolean
        get() = prefs.getBoolean(KEY_GRID_STATS, true)
        set(value) = prefs.edit { putBoolean(KEY_GRID_STATS, value) }
    
    // Show info button on thumbnails
    var gridInfo: Boolean
        get() = prefs.getBoolean(KEY_GRID_INFO, true)
        set(value) = prefs.edit { putBoolean(KEY_GRID_INFO, value) }
    
    // Show "New" label on new posts
    var gridNewLabel: Boolean
        get() = prefs.getBoolean(KEY_GRID_NEW_LABEL, true)
        set(value) = prefs.edit { putBoolean(KEY_GRID_NEW_LABEL, value) }
    
    // Darken viewed posts
    var gridDarkenSeen: Boolean
        get() = prefs.getBoolean(KEY_GRID_DARKEN_SEEN, true)
        set(value) = prefs.edit { putBoolean(KEY_GRID_DARKEN_SEEN, value) }
    
    // Hide viewed posts from grid
    var gridHideSeen: Boolean
        get() = prefs.getBoolean(KEY_GRID_HIDE_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_GRID_HIDE_SEEN, value) }
    
    // Show rating/status colors
    var gridColours: Boolean
        get() = prefs.getBoolean(KEY_GRID_COLOURS, true)
        set(value) = prefs.edit { putBoolean(KEY_GRID_COLOURS, value) }
    
    // Enable pull-to-refresh
    var gridRefresh: Boolean
        get() = prefs.getBoolean(KEY_GRID_REFRESH, true)
        set(value) = prefs.edit { putBoolean(KEY_GRID_REFRESH, value) }
    
    // Grid height percentage (100-200)
    var gridHeight: Int
        get() = prefs.getInt(KEY_GRID_HEIGHT, 110)
        set(value) = prefs.edit { putInt(KEY_GRID_HEIGHT, value) }
    
    // Grid width (columns 1-9)
    var gridWidth: Int
        get() = prefs.getInt(KEY_GRID_WIDTH, 3)
        set(value) = prefs.edit { putInt(KEY_GRID_WIDTH, value) }
    
    // Posts per page (45-320)
    var gridPostsCount: Int
        get() = prefs.getInt(KEY_GRID_POSTS_COUNT, 75)
        set(value) = prefs.edit { putInt(KEY_GRID_POSTS_COUNT, value) }
    
    // Animate GIFs in grid
    var gridGifs: Boolean
        get() = prefs.getBoolean(KEY_GRID_GIFS, false)
        set(value) = prefs.edit { putBoolean(KEY_GRID_GIFS, value) }
    
    // Show next/previous navigation buttons
    var gridNavigate: Boolean
        get() = prefs.getBoolean(KEY_GRID_NAVIGATE, false)
        set(value) = prefs.edit { putBoolean(KEY_GRID_NAVIGATE, value) }
    
    // ==================== Filter Preferences ====================
    
    /**
     * Rating filter as bitmask: 1=explicit, 2=questionable, 4=safe
     * Default 7 = all ratings shown
     */
    var filterRating: Int
        get() {
            val value = prefs.getInt(KEY_FILTER_RATING, 7)
            return if (value < 1 || value > 7) 7 else value
        }
        set(value) = prefs.edit { putInt(KEY_FILTER_RATING, value) }
    
    /**
     * Type filter: 0=all, 1=images, 2=videos, 3=gifs
     */
    var filterType: Int
        get() {
            val value = prefs.getInt(KEY_FILTER_TYPE, 0)
            return if (value < 0 || value > 3) 0 else value
        }
        set(value) = prefs.edit { putInt(KEY_FILTER_TYPE, value) }
    
    /**
     * Order filter: 0=newest, 1=oldest, 2=score, 3=favcount
     */
    var filterOrder: Int
        get() {
            val value = prefs.getInt(KEY_FILTER_ORDER, 0)
            return if (value < 0 || value > 3) 0 else value
        }
        set(value) = prefs.edit { putInt(KEY_FILTER_ORDER, value) }
    
    /**
     * Set rating filter by individual flags
     */
    fun setRatingFilter(showExplicit: Boolean, showQuestionable: Boolean, showSafe: Boolean) {
        var rating = 0
        if (showExplicit) rating = rating or 1
        if (showQuestionable) rating = rating or 2
        if (showSafe) rating = rating or 4
        if (rating == 0) rating = 7 // At least one must be selected
        filterRating = rating
    }
    
    fun showExplicit(): Boolean = (filterRating and 1) != 0
    fun showQuestionable(): Boolean = (filterRating and 2) != 0
    fun showSafe(): Boolean = (filterRating and 4) != 0
    
    // ==================== Post View Preferences ====================
    
    // Expand tags section automatically
    var postExpandTags: Boolean
        get() = prefs.getBoolean(KEY_POST_EXPAND_TAGS, true)
        set(value) = prefs.edit { putBoolean(KEY_POST_EXPAND_TAGS, value) }
    
    // Expand description section automatically
    var postExpandDescription: Boolean
        get() = prefs.getBoolean(KEY_POST_EXPAND_DESCRIPTION, true)
        set(value) = prefs.edit { putBoolean(KEY_POST_EXPAND_DESCRIPTION, value) }
    
    // Show back button in post view
    var postBackButton: Boolean
        get() = prefs.getBoolean(KEY_POST_BACK_BUTTON, true)
        set(value) = prefs.edit { putBoolean(KEY_POST_BACK_BUTTON, value) }
    
    // Load high quality images
    var postLoadHQ: Boolean
        get() = prefs.getBoolean(KEY_POST_LOAD_HQ, true)
        set(value) = prefs.edit { putBoolean(KEY_POST_LOAD_HQ, value) }
    
    // Edge click navigation
    var postEdgeNavigation: Boolean
        get() = prefs.getBoolean(KEY_POST_EDGE_NAVIGATION, true)
        set(value) = prefs.edit { putBoolean(KEY_POST_EDGE_NAVIGATION, value) }
    
    // Back button location (0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right)
    var postBackButtonLocation: String
        get() = prefs.getString(KEY_POST_BACK_BUTTON_LOCATION, "0") ?: "0"
        set(value) = prefs.edit { putString(KEY_POST_BACK_BUTTON_LOCATION, value) }
    
    // Keep screen awake when viewing posts
    var postKeepScreenAwake: Boolean
        get() = prefs.getBoolean(KEY_POST_KEEP_SCREEN_AWAKE, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_KEEP_SCREEN_AWAKE, value) }
    
    // Hide status bar when viewing posts
    var postHideStatusBar: Boolean
        get() = prefs.getBoolean(KEY_POST_HIDE_STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_HIDE_STATUS_BAR, value) }
    
    // Hide navigation bar when viewing posts
    var postHideNavBar: Boolean
        get() = prefs.getBoolean(KEY_POST_HIDE_NAV_BAR, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_HIDE_NAV_BAR, value) }
    
    // Default video quality (0=original, 1=720p, 2=480p, 3=auto)
    var postDefaultVideoQuality: String
        get() = prefs.getString(KEY_POST_DEFAULT_VIDEO_QUALITY, "1") ?: "1"
        set(value) = prefs.edit { putString(KEY_POST_DEFAULT_VIDEO_QUALITY, value) }
    
    // Default video format (0=webm, 1=mp4, 2=auto)
    var postDefaultVideoFormat: String
        get() = prefs.getString(KEY_POST_DEFAULT_VIDEO_FORMAT, "0") ?: "0"
        set(value) = prefs.edit { putString(KEY_POST_DEFAULT_VIDEO_FORMAT, value) }
    
    // Mute videos by default
    var postMuteVideos: Boolean
        get() = prefs.getBoolean(KEY_POST_MUTE_VIDEOS, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_MUTE_VIDEOS, value) }
    
    // Autoplay videos
    var postAutoplayVideos: Boolean
        get() = prefs.getBoolean(KEY_POST_AUTOPLAY_VIDEOS, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_AUTOPLAY_VIDEOS, value) }
    
    // Force landscape for videos
    var postLandscapeVideos: Boolean
        get() = prefs.getBoolean(KEY_POST_LANDSCAPE_VIDEOS, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_LANDSCAPE_VIDEOS, value) }
    
    // Fullscreen videos
    var postFullscreenVideos: Boolean
        get() = prefs.getBoolean(KEY_POST_FULLSCREEN_VIDEOS, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_FULLSCREEN_VIDEOS, value) }
    
    // Upvote when favoriting
    var postActionUpvoteOnFav: Boolean
        get() = prefs.getBoolean(KEY_POST_ACTION_UPVOTE_ON_FAV, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_ACTION_UPVOTE_ON_FAV, value) }
    
    // Upvote when downloading
    var postActionUpvoteOnDownload: Boolean
        get() = prefs.getBoolean(KEY_POST_ACTION_UPVOTE_ON_DOWNLOAD, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_ACTION_UPVOTE_ON_DOWNLOAD, value) }
    
    // Favorite when downloading
    var postActionFavOnDownload: Boolean
        get() = prefs.getBoolean(KEY_POST_ACTION_FAV_ON_DOWNLOAD, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_ACTION_FAV_ON_DOWNLOAD, value) }
    
    // Long click to unfavorite
    var postLongClickToUnfav: Boolean
        get() = prefs.getBoolean(KEY_POST_LONG_CLICK_TO_UNFAV, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_LONG_CLICK_TO_UNFAV, value) }
    
    // Hide post score
    var postHideScore: Boolean
        get() = prefs.getBoolean(KEY_POST_HIDE_SCORE, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_HIDE_SCORE, value) }
    
    // Top preview on scroll
    var postTopPreview: Boolean
        get() = prefs.getBoolean(KEY_POST_TOP_PREVIEW, true)
        set(value) = prefs.edit { putBoolean(KEY_POST_TOP_PREVIEW, value) }
    
    // Show controls in fullscreen mode
    var postControlsFullscreen: Boolean
        get() = prefs.getBoolean(KEY_POST_CONTROLS_FULLSCREEN, true)
        set(value) = prefs.edit { putBoolean(KEY_POST_CONTROLS_FULLSCREEN, value) }
    
    // Hide comments section
    var postHideComments: Boolean
        get() = prefs.getBoolean(KEY_POST_HIDE_COMMENTS, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_HIDE_COMMENTS, value) }
    
    // Enable pull to close gesture
    var postPullToClose: Boolean
        get() = prefs.getBoolean(KEY_POST_PULL_TO_CLOSE, true)
        set(value) = prefs.edit { putBoolean(KEY_POST_PULL_TO_CLOSE, value) }
    
    // Data saver mode (use preview instead of full image)
    var postDataSaver: Boolean
        get() = prefs.getBoolean(KEY_POST_DATA_SAVER, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_DATA_SAVER, value) }
    
    // Disable share button
    var postDisableShare: Boolean
        get() = prefs.getBoolean(KEY_POST_DISABLE_SHARE, false)
        set(value) = prefs.edit { putBoolean(KEY_POST_DISABLE_SHARE, value) }

    // ==================== Storage Preferences ====================
    
    // Enable custom download folder
    var storageCustomFolderEnabled: Boolean
        get() = prefs.getBoolean(KEY_STORAGE_CUSTOM_FOLDER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_STORAGE_CUSTOM_FOLDER_ENABLED, value) }
    
    // Custom folder URI (SAF document tree URI)
    var storageCustomFolder: String?
        get() = prefs.getString(KEY_STORAGE_CUSTOM_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_STORAGE_CUSTOM_FOLDER, value) }
    
    // File name mask template
    var storageFileNameMask: String
        get() = prefs.getString(KEY_STORAGE_FILE_NAME_MASK, "%artist%-%id%") ?: "%artist%-%id%"
        set(value) = prefs.edit { putString(KEY_STORAGE_FILE_NAME_MASK, value) }
    
    // Overwrite existing files
    var storageOverwrite: Boolean
        get() = prefs.getBoolean(KEY_STORAGE_OVERWRITE, true)
        set(value) = prefs.edit { putBoolean(KEY_STORAGE_OVERWRITE, value) }
    
    // Hide files from gallery (add . prefix)
    var storageHide: Boolean
        get() = prefs.getBoolean(KEY_STORAGE_HIDE, false)
        set(value) = prefs.edit { putBoolean(KEY_STORAGE_HIDE, value) }
    
    // Enable cache size limit
    var storageMaxCache: Boolean
        get() = prefs.getBoolean(KEY_STORAGE_MAX_CACHE, true)
        set(value) = prefs.edit { putBoolean(KEY_STORAGE_MAX_CACHE, value) }
    
    // Max cache size in MB
    var storageMaxCacheSize: Int
        get() = prefs.getInt(KEY_STORAGE_MAX_CACHE_SIZE, 500)
        set(value) = prefs.edit { putInt(KEY_STORAGE_MAX_CACHE_SIZE, value) }

    // ==================== Privacy Preferences ====================
    
    // Send crash reports
    var privacyCrashReports: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_CRASH_REPORTS, true)
        set(value) = prefs.edit { putBoolean(KEY_PRIVACY_CRASH_REPORTS, value) }
    
    // Send analytics
    var privacyAnalytics: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_ANALYTICS, true)
        set(value) = prefs.edit { putBoolean(KEY_PRIVACY_ANALYTICS, value) }

    // ==================== Proxy Preferences ====================
    
    // Proxy enabled
    var proxyEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROXY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_PROXY_ENABLED, value) }
    
    // Proxy host
    var proxyHost: String?
        get() = prefs.getString(KEY_PROXY_HOST, null)
        set(value) = prefs.edit { putString(KEY_PROXY_HOST, value) }
    
    // Proxy port
    var proxyPort: Int
        get() = prefs.getInt(KEY_PROXY_PORT, -1)
        set(value) = prefs.edit { putInt(KEY_PROXY_PORT, value) }
    
    // Proxy username (optional)
    var proxyUsername: String?
        get() = prefs.getString(KEY_PROXY_USERNAME, null)
        set(value) = prefs.edit { putString(KEY_PROXY_USERNAME, value) }
    
    // Proxy password (optional)
    var proxyPassword: String?
        get() = prefs.getString(KEY_PROXY_PASSWORD, null)
        set(value) = prefs.edit { putString(KEY_PROXY_PASSWORD, value) }
    
    /**
     * Get proxy configuration if enabled
     * Returns null if proxy is not enabled or not configured
     */
    fun getProxyConfig(): ProxyConfig? {
        if (!proxyEnabled) return null
        val host = proxyHost ?: return null
        val port = proxyPort
        if (port < 0) return null
        return ProxyConfig(host, port, proxyUsername, proxyPassword)
    }
    
    /**
     * Proxy configuration data class
     */
    data class ProxyConfig(
        val host: String,
        val port: Int,
        val username: String?,
        val password: String?
    )

    // ==================== General Preferences ====================
    
    // Language
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }
    
    // Host (e621.net or e926.net)
    var host: String
        get() = prefs.getString(KEY_HOST, "e926.net") ?: "e926.net"
        set(value) = prefs.edit { putString(KEY_HOST, value) }
    
    // Age consent (18+)
    var ageConsent: Boolean
        get() = prefs.getBoolean(KEY_AGE_CONSENT, false)
        set(value) = prefs.edit { putBoolean(KEY_AGE_CONSENT, value) }
    
    // Post quality (0=low, 1=medium, 2=high)
    var postQuality: Int
        get() = prefs.getInt(KEY_POST_QUALITY, 1)
        set(value) = prefs.edit { putInt(KEY_POST_QUALITY, value) }
    
    // Thumbnail quality (0=low, 1=medium, 2=high)
    var thumbQuality: Int
        get() = prefs.getInt(KEY_THUMB_QUALITY, 0)
        set(value) = prefs.edit { putInt(KEY_THUMB_QUALITY, value) }
    
    // Blacklist enabled
    var blacklistEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLACKLIST_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_BLACKLIST_ENABLED, value) }
    
    // Blacklist pool posts
    var blacklistPoolPosts: Boolean
        get() = prefs.getBoolean(KEY_BLACKLIST_POOL_POSTS, false)
        set(value) = prefs.edit { putBoolean(KEY_BLACKLIST_POOL_POSTS, value) }
    
    // Hide app in recent tasks
    var hideInTasks: Boolean
        get() = prefs.getBoolean(KEY_HIDE_IN_TASKS, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDE_IN_TASKS, value) }
    
    // Disguise app (change icon/name)
    var disguiseMode: Boolean
        get() = prefs.getBoolean(KEY_DISGUISE_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_DISGUISE_MODE, value) }
    
    // Start in saved searches
    var startInSaved: Boolean
        get() = prefs.getBoolean(KEY_START_IN_SAVED, false)
        set(value) = prefs.edit { putBoolean(KEY_START_IN_SAVED, value) }
    
    // PIN unlock
    var pinUnlock: Boolean
        get() = prefs.getBoolean(KEY_PIN_UNLOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_PIN_UNLOCK, value) }
    
    // PIN value (4 digits)
    var pin: Int
        get() = prefs.getInt(KEY_PIN, -1)
        set(value) = prefs.edit { putInt(KEY_PIN, value) }
    
    // PIN app link (lock when opened from link)
    var pinAppLink: Boolean
        get() = prefs.getBoolean(KEY_PIN_APP_LINK, false)
        set(value) = prefs.edit { putBoolean(KEY_PIN_APP_LINK, value) }
    
    // Biometrics unlock enabled
    var biometricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRICS, true)
        set(value) = prefs.edit { putBoolean(KEY_BIOMETRICS, value) }
    
    // Auto lock on app switch
    var autoLock: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_LOCK, value) }
    
    // Auto lock instantly vs delay
    var autoLockInstantly: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOCK_INSTANTLY, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_LOCK_INSTANTLY, value) }
    
    /**
     * Check if PIN is set and valid
     */
    fun isPinSet(): Boolean = pinUnlock && pin in 0..9999
    
    /**
     * Format PIN with leading zeros
     */
    fun getFormattedPin(): String {
        val pinValue = pin
        return if (pinValue < 0) "----"
        else String.format("%04d", pinValue)
    }

    /**
     * Check if user is logged in
     */
    val isLoggedIn: Boolean
        get() = !username.isNullOrEmpty() && !apiKey.isNullOrEmpty()

    /**
     * Get base URL based on safe mode
     */
    val baseUrl: String
        get() = if (safeMode) BASE_URL_SAFE else BASE_URL_EXPLICIT

    /**
     * Get authorization header value
     */
    fun getAuthHeader(): String? {
        val user = username ?: return null
        val key = apiKey ?: return null
        val credentials = "$user:$key"
        return "Basic " + android.util.Base64.encodeToString(
            credentials.toByteArray(),
            android.util.Base64.NO_WRAP
        )
    }

    /**
     * Save login credentials
     */
    fun saveCredentials(username: String, apiKey: String) {
        this.username = username
        this.apiKey = apiKey
    }

    /**
     * Clear all credentials (logout)
     */
    fun clearCredentials() {
        username = null
        apiKey = null
    }

    /**
     * Clear all preferences
     */
    fun clearAll() {
        prefs.edit { clear() }
    }

    // ==================== Search History ====================
    
    /**
     * Get search history as list (max 20 items)
     */
    val searchHistory: List<String>
        get() {
            val historyString = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
            return if (historyString.isEmpty()) emptyList()
            else historyString.split(HISTORY_SEPARATOR).filter { it.isNotBlank() }
        }
    
    /**
     * Add a search query to history
     */
    fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        
        val currentHistory = searchHistory.toMutableList()
        
        // Remove if already exists (to move to top)
        currentHistory.remove(query)
        
        // Add at beginning
        currentHistory.add(0, query)
        
        // Keep only last 20 items
        val limitedHistory = currentHistory.take(20)
        
        // Save as string
        prefs.edit { 
            putString(KEY_SEARCH_HISTORY, limitedHistory.joinToString(HISTORY_SEPARATOR))
        }
    }
    
    /**
     * Clear search history
     */
    fun clearSearchHistory() {
        prefs.edit { remove(KEY_SEARCH_HISTORY) }
    }

    // ==================== Viewed Posts ====================
    
    /**
     * Get set of viewed post IDs
     */
    private val viewedPostIds: MutableSet<String>
        get() = prefs.getStringSet(KEY_VIEWED_POSTS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    
    /**
     * Mark a post as viewed
     */
    fun markPostAsViewed(postId: Int) {
        val current = viewedPostIds
        current.add(postId.toString())
        prefs.edit { putStringSet(KEY_VIEWED_POSTS, current) }
    }
    
    /**
     * Check if post was viewed
     */
    fun isPostViewed(postId: Int): Boolean {
        return viewedPostIds.contains(postId.toString())
    }
    
    /**
     * Get count of viewed posts
     */
    fun getViewedPostsCount(): Int {
        return viewedPostIds.size
    }
    
    /**
     * Clear all viewed posts
     */
    fun clearViewedPosts() {
        prefs.edit { remove(KEY_VIEWED_POSTS) }
    }

    companion object {
        private const val PREFS_NAME = "e621_client_prefs"
        
        private const val KEY_USERNAME = "username"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SAFE_MODE = "safe_mode"
        private const val KEY_THEME = "theme"
        private const val KEY_IMAGE_QUALITY = "image_quality"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_BLACKLIST = "blacklist"
        private const val KEY_SAVED_SEARCHES = "saved_searches"
        private const val KEY_FOLLOWED_TAGS = "followed_tags"
        private const val KEY_LAST_SEEN_POST_IDS = "last_seen_post_ids"
        private const val KEY_FOLLOWED_TAGS_NOTIFICATIONS = "followed_tags_notifications"
        private const val KEY_FOLLOWED_TAGS_INTERVAL = "followed_tags_interval"
        private const val KEY_FOLLOWING_ONLY_WIFI = "following_only_wifi"
        private const val KEY_FOLLOWING_DISPLAY_TAG = "following_display_tag"
        private const val KEY_FOLLOWING_DISPLAY_IN_SAVED_SEARCH = "following_display_in_saved_search"
        private const val KEY_LAST_SEARCH = "last_search"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_VIEWED_POSTS = "viewed_posts"
        private const val HISTORY_SEPARATOR = "|||"
        
        // Search preferences keys
        private const val KEY_SEARCH_HISTORY_ENABLED = "search_history_enabled"
        private const val KEY_SEARCH_SUGGESTIONS = "search_suggestions"
        private const val KEY_SEARCH_SAVED_NEW_WINDOW = "search_saved_new_window"
        private const val KEY_SEARCH_LAST_ON_START = "search_last_on_start"
        private const val KEY_SEARCH_IN_NEW_TASK = "search_in_new_task"
        private const val KEY_SEARCH_FAV_ORDER = "grid_fav_order"
        private const val KEY_SEARCH_NEWEST_FIRST = "search_newest_first"
        private const val KEY_SEARCH_INCLUDE_FLASH = "search_include_flash"
        
        // Grid preferences keys
        private const val KEY_GRID_STATS = "grid_stats"
        private const val KEY_GRID_INFO = "grid_info"
        private const val KEY_GRID_NEW_LABEL = "grid_new_label"
        private const val KEY_GRID_DARKEN_SEEN = "grid_darken_seen"
        private const val KEY_GRID_HIDE_SEEN = "grid_hide_seen"
        private const val KEY_GRID_COLOURS = "grid_colours"
        private const val KEY_GRID_REFRESH = "grid_refresh"
        private const val KEY_GRID_HEIGHT = "grid_height"
        private const val KEY_GRID_WIDTH = "grid_width"
        private const val KEY_GRID_POSTS_COUNT = "search_posts_count"
        private const val KEY_GRID_GIFS = "grid_gifs"
        private const val KEY_GRID_NAVIGATE = "grid_navigate"
        
        // Post view preferences keys
        private const val KEY_POST_EXPAND_TAGS = "post_expand_tags"
        private const val KEY_POST_EXPAND_DESCRIPTION = "post_expand_description"
        private const val KEY_POST_BACK_BUTTON = "post_back_button"
        private const val KEY_POST_LOAD_HQ = "post_load_hq"
        private const val KEY_POST_EDGE_NAVIGATION = "post_edge_navigation"
        private const val KEY_POST_BACK_BUTTON_LOCATION = "post_back_button_location"
        private const val KEY_POST_KEEP_SCREEN_AWAKE = "post_keep_screen_awake"
        private const val KEY_POST_HIDE_STATUS_BAR = "post_hide_status_bar"
        private const val KEY_POST_HIDE_NAV_BAR = "post_hide_nav_bar"
        private const val KEY_POST_DEFAULT_VIDEO_QUALITY = "post_default_video_quality"
        private const val KEY_POST_DEFAULT_VIDEO_FORMAT = "post_default_video_format"
        private const val KEY_POST_MUTE_VIDEOS = "post_mute_videos"
        private const val KEY_POST_AUTOPLAY_VIDEOS = "post_autoplay_videos"
        private const val KEY_POST_LANDSCAPE_VIDEOS = "post_landscape_videos"
        private const val KEY_POST_FULLSCREEN_VIDEOS = "post_fullscreen_videos"
        private const val KEY_POST_ACTION_UPVOTE_ON_FAV = "post_action_upvote_on_fav"
        private const val KEY_POST_ACTION_UPVOTE_ON_DOWNLOAD = "post_action_upvote_on_download"
        private const val KEY_POST_ACTION_FAV_ON_DOWNLOAD = "post_action_fav_on_download"
        private const val KEY_POST_LONG_CLICK_TO_UNFAV = "post_long_click_to_unfav"
        private const val KEY_POST_HIDE_SCORE = "post_hide_score"
        private const val KEY_POST_TOP_PREVIEW = "post_top_preview"
        private const val KEY_POST_CONTROLS_FULLSCREEN = "post_controls_fullscreen"
        private const val KEY_POST_HIDE_COMMENTS = "post_hide_comments"
        private const val KEY_POST_PULL_TO_CLOSE = "post_pull_to_close"
        private const val KEY_POST_DATA_SAVER = "post_data_saver"
        private const val KEY_POST_DISABLE_SHARE = "post_disable_share"
        
        // Storage preferences keys
        private const val KEY_STORAGE_CUSTOM_FOLDER_ENABLED = "storage_custom_folder_enabled"
        private const val KEY_STORAGE_CUSTOM_FOLDER = "storage_custom_folder"
        private const val KEY_STORAGE_FILE_NAME_MASK = "storage_file_name_mask"
        private const val KEY_STORAGE_OVERWRITE = "storage_overwrite"
        private const val KEY_STORAGE_HIDE = "storage_hide"
        private const val KEY_STORAGE_MAX_CACHE = "storage_max_cache"
        private const val KEY_STORAGE_MAX_CACHE_SIZE = "storage_max_cache_slider"
        
        // Privacy preferences keys
        private const val KEY_PRIVACY_CRASH_REPORTS = "privacy_crash_reports"
        private const val KEY_PRIVACY_ANALYTICS = "privacy_analytics"
        
        // Proxy preferences keys
        private const val KEY_PROXY_ENABLED = "proxy"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_USERNAME = "proxy_username"
        private const val KEY_PROXY_PASSWORD = "proxy_password"
        
        // Filter preferences keys
        private const val KEY_FILTER_RATING = "filter_rating"
        private const val KEY_FILTER_TYPE = "filter_type"
        private const val KEY_FILTER_ORDER = "filter_order"
        
        // General preferences keys
        private const val KEY_LANGUAGE = "general_language"
        private const val KEY_HOST = "general_change_host"
        private const val KEY_AGE_CONSENT = "consent_above_18"
        private const val KEY_POST_QUALITY = "general_post_quality"
        private const val KEY_THUMB_QUALITY = "general_thumb_quality"
        private const val KEY_BLACKLIST_ENABLED = "general_blacklist_enabled"
        private const val KEY_BLACKLIST_POOL_POSTS = "general_blacklist_pool_posts"
        private const val KEY_HIDE_IN_TASKS = "general_hide_in_tasks"
        private const val KEY_DISGUISE_MODE = "general_disguise"
        private const val KEY_START_IN_SAVED = "general_start_in_saved"
        private const val KEY_PIN_UNLOCK = "consent_pin_unlock"
        private const val KEY_PIN = "pin"
        private const val KEY_PIN_APP_LINK = "consent_pin_app_link"
        private const val KEY_BIOMETRICS = "consent_biometrics"
        private const val KEY_AUTO_LOCK = "consent_pin_auto_lock"
        private const val KEY_AUTO_LOCK_INSTANTLY = "consent_pin_auto_lock_instantly"
        
        const val BASE_URL_EXPLICIT = "https://e621.net/"
        const val BASE_URL_SAFE = "https://e926.net/"
    }
}
