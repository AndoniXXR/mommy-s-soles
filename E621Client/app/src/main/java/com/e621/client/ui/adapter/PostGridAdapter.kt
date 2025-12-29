package com.e621.client.ui.adapter

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
 * Adapter for displaying posts in a grid with multi-selection support
 * Based on original app's adapter_grid_item.xml design
 */
class PostGridAdapter(
    private val posts: List<Post>,
    private val selectedPosts: MutableSet<Int>,
    private val listener: OnPostClickListener
) : RecyclerView.Adapter<PostGridAdapter.PostViewHolder>() {

    interface OnPostClickListener {
        fun onPostClick(post: Post, position: Int)
        fun onPostLongClick(post: Post, position: Int): Boolean
        fun onSelectionChanged()
    }
    
    val isInSelectionMode: Boolean
        get() = selectedPosts.isNotEmpty()

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val imgTypeIndicator: ImageView = itemView.findViewById(R.id.imgTypeIndicator)
        val imgSelected: ImageView = itemView.findViewById(R.id.imgSelected)
        val txtInfo: TextView = itemView.findViewById(R.id.txtInfo)
        val viewRating: View = itemView.findViewById(R.id.viewRating)
        val fLOverlay: FrameLayout = itemView.findViewById(R.id.fLOverlay)
        
        fun bind(post: Post) {
            // Load thumbnail
            Glide.with(itemView.context)
                .load(post.getThumbnailUrl())
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .centerCrop()
                .into(imageView)
            
            // Show type indicator for videos/gifs (top right, like original)
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
            
            // Show score (like original app)
            val score = post.score.total
            if (score != 0) {
                txtInfo.visibility = View.VISIBLE
                txtInfo.text = if (score > 0) "↑$score" else "↓$score"
            } else {
                txtInfo.visibility = View.GONE
            }
            
            // Rating indicator color (bottom line)
            val ratingColor = when (post.rating) {
                "s" -> R.color.rating_safe
                "q" -> R.color.rating_questionable
                else -> R.color.rating_explicit
            }
            viewRating.setBackgroundColor(ContextCompat.getColor(itemView.context, ratingColor))
            
            // Selection state
            val isSelected = selectedPosts.contains(post.id)
            imgSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            fLOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Click listeners
            itemView.setOnClickListener {
                if (isInSelectionMode) {
                    // In selection mode, clicking toggles selection
                    toggleSelection(post)
                } else {
                    listener.onPostClick(post, bindingAdapterPosition)
                }
            }
            itemView.setOnLongClickListener {
                toggleSelection(post)
                true
            }
        }
        
        private fun toggleSelection(post: Post) {
            val isCurrentlySelected = selectedPosts.contains(post.id)
            
            if (isCurrentlySelected) {
                selectedPosts.remove(post.id)
            } else {
                selectedPosts.add(post.id)
            }
            
            // Update UI
            imgSelected.visibility = if (!isCurrentlySelected) View.VISIBLE else View.GONE
            fLOverlay.visibility = if (!isCurrentlySelected) View.VISIBLE else View.GONE
            
            // Vibration feedback
            vibrate()
            
            // Notify listener
            listener.onSelectionChanged()
        }
        
        private fun vibrate() {
            val vibrator = itemView.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(5)
                }
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
    
    fun clearSelection() {
        selectedPosts.clear()
        notifyDataSetChanged()
        listener.onSelectionChanged()
    }
    
    fun getSelectedPosts(): List<Post> {
        return posts.filter { selectedPosts.contains(it.id) }
    }
}
