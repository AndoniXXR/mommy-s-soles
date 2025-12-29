package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a note on a post (text overlaid on the image)
 */
data class Note(
    @SerializedName("id") val id: Int,
    @SerializedName("post_id") val postId: Int,
    @SerializedName("body") val body: String,
    @SerializedName("x") val x: Int,
    @SerializedName("y") val y: Int,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("version") val version: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("creator_id") val creatorId: Int?,
    @SerializedName("creator_name") val creatorName: String?
)
