package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * PostSet model based on e621 API
 */
data class PostSet(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?,
    
    @SerializedName("creator_id")
    val creatorId: Int,
    
    @SerializedName("is_public")
    val isPublic: Boolean,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("shortname")
    val shortname: String,
    
    @SerializedName("description")
    val description: String?,
    
    @SerializedName("post_count")
    val postCount: Int,
    
    @SerializedName("transfer_on_delete")
    val transferOnDelete: Boolean?,
    
    @SerializedName("post_ids")
    val postIds: List<Int>?
)

/**
 * Response wrapper for post sets (when no results)
 */
data class PostSetsNoResultResponse(
    @SerializedName("post_sets")
    val postSets: List<PostSet>? = null
)

/**
 * Sets for select response (Owned / Maintained)
 */
data class SetsForSelectResponse(
    @SerializedName("Owned")
    val owned: List<List<Any>>?,
    @SerializedName("Maintained")
    val maintained: List<List<Any>>?
)
