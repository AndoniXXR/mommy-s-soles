package com.e621.client.ui.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.R
import com.e621.client.data.model.Dmail
import java.text.SimpleDateFormat
import java.util.Locale

class DmailAdapter(
    private val onDmailClick: (Dmail) -> Unit
) : ListAdapter<Dmail, DmailAdapter.DmailViewHolder>(DmailDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DmailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dmail, parent, false)
        return DmailViewHolder(view)
    }

    override fun onBindViewHolder(holder: DmailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DmailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mailIcon: ImageView = itemView.findViewById(R.id.mailIcon)
        private val fromText: TextView = itemView.findViewById(R.id.fromText)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val previewText: TextView = itemView.findViewById(R.id.previewText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val unreadIndicator: View = itemView.findViewById(R.id.unreadIndicator)

        fun bind(dmail: Dmail) {
            fromText.text = dmail.fromName ?: "User #${dmail.fromId}"
            titleText.text = dmail.title
            previewText.text = dmail.body?.take(100) ?: ""
            
            // Format date
            dmail.createdAt?.let { dateString ->
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
                    val outputFormat = SimpleDateFormat("MMM dd", Locale.US)
                    val date = inputFormat.parse(dateString)
                    dateText.text = date?.let { outputFormat.format(it) } ?: dateString
                } catch (e: Exception) {
                    dateText.text = dateString.take(10)
                }
            }

            // Unread styling
            val isUnread = dmail.isRead == false
            unreadIndicator.visibility = if (isUnread) View.VISIBLE else View.GONE
            fromText.setTypeface(null, if (isUnread) Typeface.BOLD else Typeface.NORMAL)
            titleText.setTypeface(null, if (isUnread) Typeface.BOLD else Typeface.NORMAL)

            itemView.setOnClickListener { onDmailClick(dmail) }
        }
    }

    private class DmailDiffCallback : DiffUtil.ItemCallback<Dmail>() {
        override fun areItemsTheSame(oldItem: Dmail, newItem: Dmail): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Dmail, newItem: Dmail): Boolean {
            return oldItem == newItem
        }
    }
}
