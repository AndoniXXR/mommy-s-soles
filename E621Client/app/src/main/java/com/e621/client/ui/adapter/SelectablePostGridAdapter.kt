package com.e621.client.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.e621.client.R
import com.e621.client.data.model.Post

/**
 * Adapter for displaying posts in a grid with selection support
 * Used for Popular and other multi-select screens
 */
class SelectablePostGridAdapter(
    private val posts: List<Post>,
    private val selectedPosts: MutableSet<Int>,
    private val onPostClick: (Post) -> Unit,
    private val onPostLongClick: (Post) -> Unit,
    private val onSelectionChange: () -> Unit
) : RecyclerView.Adapter<SelectablePostGridAdapter.PostViewHolder>() {

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val imgTypeIndicator: ImageView = itemView.findViewById(R.id.imgTypeIndicator)
        val txtInfo: TextView = itemView.findViewById(R.id.txtInfo)
        val viewRating: View = itemView.findViewById(R.id.viewRating)
        val fLOverlay: FrameLayout = itemView.findViewById(R.id.fLOverlay)
        
        fun bind(post: Post) {
            val isSelected = selectedPosts.contains(post.id)
            
            // Load thumbnail
            Glide.with(itemView.context)
                .load(post.getThumbnailUrl())
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .centerCrop()
                .into(imageView)
            
            // Show type indicator for videos/gifs
            when {
                post.isVideo() -> {
                    imgTypeIndicator.visibility = View.VISIBLE
                    imgTypeIndicator.setImageResource(R.drawable.ic_video)
                }
                post.isGif() -> {
                    imgTypeIndicator.visibility = View.VISIBLE
                    imgTypeIndicator.setImageResource(R.drawable.ic_gif)
                }
                else -> {
                    imgTypeIndicator.visibility = View.GONE
                }
            }
            
            // Show score
            val score = post.score.total
            if (score != 0) {
                txtInfo.visibility = View.VISIBLE
                txtInfo.text = if (score > 0) "↑$score" else "↓$score"
            } else {
                txtInfo.visibility = View.GONE
            }
            
            // Rating indicator color
            val ratingColor = when (post.rating) {
                "s" -> R.color.rating_safe
                "q" -> R.color.rating_questionable
                else -> R.color.rating_explicit
            }
            viewRating.setBackgroundColor(ContextCompat.getColor(itemView.context, ratingColor))
            
            // Selection state - use alpha and overlay tint
            imageView.alpha = if (isSelected) 0.5f else 1.0f
            fLOverlay.setBackgroundColor(
                if (isSelected) 
                    ContextCompat.getColor(itemView.context, R.color.accent_translucent) 
                else 0
            )
            
            // Click listeners
            itemView.setOnClickListener {
                onPostClick(post)
            }
            itemView.setOnLongClickListener {
                onPostLongClick(post)
                onSelectionChange()
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_grid, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int = posts.size
}
