package com.e621.client.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.e621.client.R
import com.e621.client.data.model.PostSet

class SetAdapter(
    private val onSetClick: (PostSet) -> Unit
) : ListAdapter<PostSet, SetAdapter.SetViewHolder>(SetDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_set, parent, false)
        return SetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val setThumbnail: ImageView = itemView.findViewById(R.id.setThumbnail)
        private val setNameText: TextView = itemView.findViewById(R.id.setNameText)
        private val setShortnameText: TextView = itemView.findViewById(R.id.setShortnameText)
        private val setDescriptionText: TextView = itemView.findViewById(R.id.setDescriptionText)
        private val postCountBadge: TextView = itemView.findViewById(R.id.postCountBadge)
        private val publicIcon: ImageView = itemView.findViewById(R.id.publicIcon)

        fun bind(set: PostSet) {
            setNameText.text = set.name
            setShortnameText.text = set.shortname
            setDescriptionText.text = set.description ?: ""
            setDescriptionText.visibility = if (set.description.isNullOrEmpty()) View.GONE else View.VISIBLE
            
            postCountBadge.text = set.postCount.toString()
            
            // Public/Private icon
            publicIcon.setImageResource(
                if (set.isPublic) R.drawable.ic_public else R.drawable.ic_private
            )
            publicIcon.contentDescription = itemView.context.getString(
                if (set.isPublic) R.string.public_set else R.string.set_private
            )

            // Default thumbnail placeholder
            setThumbnail.setImageResource(R.drawable.ic_image_placeholder)

            itemView.setOnClickListener { onSetClick(set) }
        }
    }

    private class SetDiffCallback : DiffUtil.ItemCallback<PostSet>() {
        override fun areItemsTheSame(oldItem: PostSet, newItem: PostSet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PostSet, newItem: PostSet): Boolean {
            return oldItem == newItem
        }
    }
}
