package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response wrapper for posts list
 */
data class PostsResponse(
    @SerializedName("posts")
    val posts: List<Post>
)

/**
 * Response wrapper for single post
 */
data class PostResponse(
    @SerializedName("post")
    val post: Post
)

/**
 * Main Post model based on e621 API
 */
data class Post(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?,
    
    @SerializedName("file")
    val file: PostFile,
    
    @SerializedName("preview")
    val preview: PostPreview,
    
    @SerializedName("sample")
    val sample: PostSample,
    
    @SerializedName("score")
    val score: PostScore,
    
    @SerializedName("tags")
    val tags: PostTags,
    
    @SerializedName("locked_tags")
    val lockedTags: List<String>?,
    
    @SerializedName("change_seq")
    val changeSeq: Long?,
    
    @SerializedName("flags")
    val flags: PostFlags,
    
    @SerializedName("rating")
    val rating: String, // s, q, e
    
    @SerializedName("fav_count")
    val favCount: Int,
    
    @SerializedName("sources")
    val sources: List<String>?,
    
    @SerializedName("pools")
    val pools: List<Int>?,
    
    @SerializedName("relationships")
    val relationships: PostRelationships,
    
    @SerializedName("approver_id")
    val approverId: Int?,
    
    @SerializedName("uploader_id")
    val uploaderId: Int,
    
    @SerializedName("description")
    val description: String?,
    
    @SerializedName("comment_count")
    val commentCount: Int,
    
    @SerializedName("is_favorited")
    var isFavorited: Boolean?,
    
    // Local tracking fields (not from API)
    @Transient
    var userVote: Int = 0, // 1 = upvoted, -1 = downvoted, 0 = no vote

    @SerializedName("has_notes")
    val hasNotes: Boolean?,
    
    @SerializedName("duration")
    val duration: Float?
) {
    /**
     * Get the best URL for display based on quality preference
     */
    fun getDisplayUrl(quality: Int): String {
        return when (quality) {
            0 -> preview.url ?: sample.url ?: file.url ?: ""
            1 -> sample.url ?: file.url ?: preview.url ?: ""
            else -> file.url ?: sample.url ?: preview.url ?: ""
        }
    }
    
    /**
     * Get thumbnail URL based on quality preference
     * @param quality 0=low (preview), 1=medium (sample), 2=high (file)
     */
    fun getThumbnailUrl(quality: Int = 0): String {
        return when (quality) {
            0 -> preview.url ?: sample.url ?: file.url ?: ""
            1 -> sample.url ?: preview.url ?: file.url ?: ""
            else -> file.url ?: sample.url ?: preview.url ?: ""
        }
    }
    
    /**
     * Check if post is a video
     */
    fun isVideo(): Boolean {
        return file.ext == "webm" || file.ext == "mp4"
    }
    
    /**
     * Check if post is an image (not video or gif)
     */
    fun isImage(): Boolean {
        return !isVideo() && !isGif()
    }
    
    /**
     * Check if post is a GIF
     */
    fun isGif(): Boolean {
        return file.ext == "gif"
    }
    
    /**
     * Get rating display name
     */
    fun getRatingName(): String {
        return when (rating) {
            "s" -> "Safe"
            "q" -> "Questionable"
            "e" -> "Explicit"
            else -> rating.uppercase()
        }
    }
    
    /**
     * Get artist tags as comma-separated string
     */
    fun getArtistString(): String {
        return tags.artist?.joinToString(", ") ?: "Unknown"
    }
    
    /**
     * Get video URL based on quality and format preferences
     * @param quality 0=original, 1=720p, 2=480p
     * @param format 0=webm, 1=mp4, 2=auto (prefers webm)
     * @return Video URL or null if not available
     */
    fun getVideoUrl(quality: Int, format: Int): String? {
        // Check if alternates are available
        val alternates = sample.alternates
        if (alternates == null || !isVideo()) {
            return file.url
        }
        
        // Determine which quality to use
        val qualityKey = when (quality) {
            0 -> "original"
            1 -> "720p"
            2 -> "480p"
            else -> "480p"
        }
        
        // Try to get the requested quality, fallback to others
        val alternate = alternates[qualityKey] 
            ?: alternates["480p"] 
            ?: alternates["720p"] 
            ?: alternates["original"]
        
        if (alternate == null || alternate.type != "video") {
            return file.url
        }
        
        val urls = alternate.urls ?: return file.url
        
        // Select format based on preference
        val webmUrl = urls.find { it?.endsWith(".webm") == true }
        val mp4Url = urls.find { it?.endsWith(".mp4") == true }
        
        return when (format) {
            0 -> webmUrl ?: mp4Url ?: file.url  // Prefer webm
            1 -> mp4Url ?: webmUrl ?: file.url  // Prefer mp4
            else -> webmUrl ?: mp4Url ?: file.url  // Auto: prefer webm
        }
    }
    
    /**
     * Check if video has alternate qualities available
     */
    fun hasVideoAlternates(): Boolean {
        return isVideo() && sample.alternates?.isNotEmpty() == true
    }
}

/**
 * File information for a post
 */
data class PostFile(
    @SerializedName("width")
    val width: Int,
    
    @SerializedName("height")
    val height: Int,
    
    @SerializedName("ext")
    val ext: String?,
    
    @SerializedName("size")
    val size: Long?,
    
    @SerializedName("md5")
    val md5: String?,
    
    @SerializedName("url")
    val url: String?
)

/**
 * Preview image information
 */
data class PostPreview(
    @SerializedName("width")
    val width: Int?,
    
    @SerializedName("height")
    val height: Int?,
    
    @SerializedName("url")
    val url: String?
)

/**
 * Sample image information
 */
data class PostSample(
    @SerializedName("has")
    val has: Boolean?,
    
    @SerializedName("width")
    val width: Int?,
    
    @SerializedName("height")
    val height: Int?,
    
    @SerializedName("url")
    val url: String?,
    
    // Alternates for video quality options (original, 720p, 480p)
    // Note: API returns false when empty, handled by custom deserializer
    @SerializedName("alternates")
    val alternates: Map<String, SampleAlternate>? = null
)

/**
 * Alternate sample versions (for videos)
 */
data class SampleAlternate(
    @SerializedName("type")
    val type: String?,
    
    @SerializedName("height")
    val height: Int?,
    
    @SerializedName("width")
    val width: Int?,
    
    @SerializedName("urls")
    val urls: List<String?>?
)

/**
 * Score information
 */
data class PostScore(
    @SerializedName("up")
    var up: Int,
    
    @SerializedName("down")
    var down: Int,
    
    @SerializedName("total")
    var total: Int
)

/**
 * All tags organized by category
 */
data class PostTags(
    @SerializedName("general")
    val general: List<String>?,
    
    @SerializedName("species")
    val species: List<String>?,
    
    @SerializedName("character")
    val character: List<String>?,
    
    @SerializedName("copyright")
    val copyright: List<String>?,
    
    @SerializedName("artist")
    val artist: List<String>?,
    
    @SerializedName("invalid")
    val invalid: List<String>?,
    
    @SerializedName("lore")
    val lore: List<String>?,
    
    @SerializedName("meta")
    val meta: List<String>?
) {
    /**
     * Get total tag count
     */
    fun getTotalCount(): Int {
        return (general?.size ?: 0) +
               (species?.size ?: 0) +
               (character?.size ?: 0) +
               (copyright?.size ?: 0) +
               (artist?.size ?: 0) +
               (invalid?.size ?: 0) +
               (lore?.size ?: 0) +
               (meta?.size ?: 0)
    }
    
    /**
     * Get all tags as a single string
     */
    fun getAllTagsString(): String {
        val allTags = mutableListOf<String>()
        artist?.let { allTags.addAll(it) }
        character?.let { allTags.addAll(it) }
        copyright?.let { allTags.addAll(it) }
        species?.let { allTags.addAll(it) }
        general?.let { allTags.addAll(it) }
        meta?.let { allTags.addAll(it) }
        lore?.let { allTags.addAll(it) }
        return allTags.joinToString(" ")
    }
}

/**
 * Post flags
 */
data class PostFlags(
    @SerializedName("pending")
    val pending: Boolean?,
    
    @SerializedName("flagged")
    val flagged: Boolean?,
    
    @SerializedName("note_locked")
    val noteLocked: Boolean?,
    
    @SerializedName("status_locked")
    val statusLocked: Boolean?,
    
    @SerializedName("rating_locked")
    val ratingLocked: Boolean?,
    
    @SerializedName("deleted")
    val deleted: Boolean?
)

/**
 * Post relationships (parent/children)
 */
data class PostRelationships(
    @SerializedName("parent_id")
    val parentId: Int?,
    
    @SerializedName("has_children")
    val hasChildren: Boolean?,
    
    @SerializedName("has_active_children")
    val hasActiveChildren: Boolean?,
    
    @SerializedName("children")
    val children: List<Int>?
)
