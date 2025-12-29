package com.e621.client.ui.browse

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.R
import com.e621.client.data.model.E621Tag

/**
 * Adapter for displaying tags in a RecyclerView
 */
class TagAdapter(
    private val onTagClick: (E621Tag) -> Unit
) : ListAdapter<E621Tag, TagAdapter.TagViewHolder>(TagDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtTagName: TextView = itemView.findViewById(R.id.txtTagName)
        private val txtTagCount: TextView = itemView.findViewById(R.id.txtTagCount)
        private val txtTagCategory: TextView = itemView.findViewById(R.id.txtTagCategory)
        
        fun bind(tag: E621Tag) {
            txtTagName.text = tag.name
            txtTagCount.text = formatCount(tag.postCount ?: 0)
            
            val categoryInfo = getCategoryInfo(tag.category ?: 0)
            txtTagCategory.text = categoryInfo.first
            txtTagName.setTextColor(categoryInfo.second)
            
            itemView.setOnClickListener {
                onTagClick(tag)
            }
        }
        
        private fun getCategoryInfo(category: Int): Pair<String, Int> {
            return when (category) {
                0 -> "General" to Color.parseColor("#00749E")
                1 -> "Artist" to Color.parseColor("#F2AC08")
                3 -> "Copyright" to Color.parseColor("#DD00DD")
                4 -> "Character" to Color.parseColor("#00AA00")
                5 -> "Species" to Color.parseColor("#ED5D1F")
                6 -> "Invalid" to Color.parseColor("#FF3D3D")
                7 -> "Meta" to Color.parseColor("#FFFFFF")
                8 -> "Lore" to Color.parseColor("#228822")
                else -> "Tag" to Color.parseColor("#00749E")
            }
        }
        
        private fun formatCount(count: Int): String {
            return when {
                count >= 1_000_000 -> String.format("%.1fM posts", count / 1_000_000.0)
                count >= 1_000 -> String.format("%.1fK posts", count / 1_000.0)
                else -> "$count posts"
            }
        }
    }

    class TagDiffCallback : DiffUtil.ItemCallback<E621Tag>() {
        override fun areItemsTheSame(oldItem: E621Tag, newItem: E621Tag): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: E621Tag, newItem: E621Tag): Boolean {
            return oldItem == newItem
        }
    }
}
