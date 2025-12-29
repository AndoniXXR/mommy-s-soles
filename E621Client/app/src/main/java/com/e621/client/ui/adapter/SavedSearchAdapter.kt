package com.e621.client.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.R

/**
 * Adapter for displaying saved searches in a RecyclerView
 */
class SavedSearchAdapter(
    private val onSearchClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : ListAdapter<String, SavedSearchAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchTags = getItem(position)
        holder.bind(searchTags)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtSearchTags: TextView = itemView.findViewById(R.id.txtSearchTags)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(searchTags: String) {
            txtSearchTags.text = searchTags
            
            itemView.setOnClickListener {
                onSearchClick(searchTags)
            }
            
            btnDelete.setOnClickListener {
                onDeleteClick(searchTags)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
