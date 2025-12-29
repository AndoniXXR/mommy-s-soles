package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * DMail (Direct Message) model based on e621 API
 */
data class Dmail(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("owner_id")
    val ownerId: Int,
    
    @SerializedName("from_id")
    val fromId: Int,
    
    @SerializedName("to_id")
    val toId: Int,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("body")
    val body: String,
    
    @SerializedName("is_read")
    val isRead: Boolean,
    
    @SerializedName("is_deleted")
    val isDeleted: Boolean,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?,
    
    @SerializedName("to_name")
    val toName: String?,
    
    @SerializedName("from_name")
    val fromName: String?
)

/**
 * Response wrapper for dmails list (when results exist)
 */
data class DmailsListResponse(
    val dmails: List<Dmail>? = null
)
