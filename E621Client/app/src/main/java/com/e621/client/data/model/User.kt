package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * User model based on e621 API
 */
data class User(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("level")
    val level: Int?,
    
    @SerializedName("base_upload_limit")
    val baseUploadLimit: Int?,
    
    @SerializedName("post_upload_count")
    val postUploadCount: Int?,
    
    @SerializedName("post_update_count")
    val postUpdateCount: Int?,
    
    @SerializedName("note_update_count")
    val noteUpdateCount: Int?,
    
    @SerializedName("is_banned")
    val isBanned: Boolean?,
    
    @SerializedName("can_approve_posts")
    val canApprovePosts: Boolean?,
    
    @SerializedName("can_upload_free")
    val canUploadFree: Boolean?,
    
    @SerializedName("level_string")
    val levelString: String?,
    
    @SerializedName("avatar_id")
    val avatarId: Int?,
    
    @SerializedName("favorite_count")
    val favoriteCount: Int?
) {
    /**
     * Get user level name
     */
    fun getLevelName(): String {
        return levelString ?: when (level) {
            0 -> "Anonymous"
            10 -> "Restricted"
            20 -> "Member"
            30 -> "Privileged"
            31 -> "Former Staff"
            32 -> "Janitor"
            33 -> "Moderator"
            34 -> "Admin"
            else -> "Unknown"
        }
    }
}
