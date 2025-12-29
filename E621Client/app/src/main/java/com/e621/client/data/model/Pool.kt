package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response wrapper for pools list
 */
data class PoolsResponse(
    val pools: List<Pool>? = null
)

/**
 * Pool model based on e621 API
 */
data class Pool(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?,
    
    @SerializedName("creator_id")
    val creatorId: Int?,
    
    @SerializedName("description")
    val description: String?,
    
    @SerializedName("is_active")
    val isActive: Boolean?,
    
    @SerializedName("category")
    val category: String?, // series, collection
    
    @SerializedName("post_ids")
    val postIds: List<Int>?,
    
    @SerializedName("creator_name")
    val creatorName: String?,
    
    @SerializedName("post_count")
    val postCount: Int?
) {
    /**
     * Get formatted display name (replace underscores with spaces)
     */
    fun getDisplayName(): String {
        return name.replace("_", " ")
    }
}
