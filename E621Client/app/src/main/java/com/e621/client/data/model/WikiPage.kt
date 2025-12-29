package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * Wiki page model from e621 API
 */
data class WikiPage(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("body")
    val body: String?,
    
    @SerializedName("creator_id")
    val creatorId: Int?,
    
    @SerializedName("creator_name")
    val creatorName: String?,
    
    @SerializedName("updater_id")
    val updaterId: Int?,
    
    @SerializedName("is_locked")
    val isLocked: Boolean?,
    
    @SerializedName("is_deleted")
    val isDeleted: Boolean?,
    
    @SerializedName("other_names")
    val otherNames: List<String>?,
    
    @SerializedName("parent")
    val parent: String?,
    
    @SerializedName("category_id")
    val categoryId: Int?
)
