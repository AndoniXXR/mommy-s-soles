package com.e621.client.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response wrapper for comments list
 */
data class CommentsResponse(
    @SerializedName("comments")
    val comments: List<Comment>? = null
)

/**
 * Comment model based on e621 API
 */
data class Comment(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("post_id")
    val postId: Int,
    
    @SerializedName("creator_id")
    val creatorId: Int,
    
    @SerializedName("body")
    val body: String,
    
    @SerializedName("score")
    val score: Int,
    
    @SerializedName("updated_at")
    val updatedAt: String?,
    
    @SerializedName("updater_id")
    val updaterId: Int?,
    
    @SerializedName("do_not_bump_post")
    val doNotBumpPost: Boolean?,
    
    @SerializedName("is_hidden")
    val isHidden: Boolean?,
    
    @SerializedName("is_sticky")
    val isSticky: Boolean?,
    
    @SerializedName("warning_type")
    val warningType: String?,
    
    @SerializedName("warning_user_id")
    val warningUserId: Int?,
    
    @SerializedName("creator_name")
    val creatorName: String?,
    
    @SerializedName("updater_name")
    val updaterName: String?
)

/**
 * Vote response for comments
 */
data class CommentVoteResponse(
    @SerializedName("score")
    val score: Int,
    @SerializedName("our_score")
    val ourScore: Int
)
