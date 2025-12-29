package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * Tag model based on e621 API
 */
data class E621Tag(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("post_count")
    val postCount: Int?,
    
    @SerializedName("related_tags")
    val relatedTags: String?,
    
    @SerializedName("related_tags_updated_at")
    val relatedTagsUpdatedAt: String?,
    
    @SerializedName("category")
    val category: Int?, // 0=general, 1=artist, 3=copyright, 4=character, 5=species, 6=invalid, 7=meta, 8=lore
    
    @SerializedName("is_locked")
    val isLocked: Boolean?,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?
) {
    /**
     * Get category name
     */
    fun getCategoryName(): String {
        return when (category) {
            0 -> "general"
            1 -> "artist"
            3 -> "copyright"
            4 -> "character"
            5 -> "species"
            6 -> "invalid"
            7 -> "meta"
            8 -> "lore"
            else -> "unknown"
        }
    }
    
    /**
     * Get display name (replace underscores)
     */
    fun getDisplayName(): String {
        return name.replace("_", " ")
    }
}

/**
 * Wrapper for tag autocomplete response
 */
data class TagAutocomplete(
    @SerializedName("id")
    val id: Int?,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("post_count")
    val postCount: Int?,
    
    @SerializedName("category")
    val category: Int?,
    
    @SerializedName("antecedent_name")
    val antecedentName: String?
)
