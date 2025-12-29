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
     * Get thumbnail URL
     */
    fun getThumbnailUrl(): String {
        return preview.url ?: sample.url ?: file.url ?: ""
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
    val url: String?
    
    // Note: 'alternates' field omitted because API returns false (boolean) 
    // when empty instead of null or empty object, causing parsing issues
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
