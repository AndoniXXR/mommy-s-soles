package com.e621.client.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.R
import com.e621.client.data.model.Comment
import java.text.SimpleDateFormat
import java.util.Locale

class CommentAdapter(
    private val onVoteUp: (Comment) -> Unit,
    private val onVoteDown: (Comment) -> Unit,
    private val onUserClick: (String) -> Unit
) : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarImage: ImageView = itemView.findViewById(R.id.avatarImage)
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val commentBody: TextView = itemView.findViewById(R.id.commentBody)
        private val scoreText: TextView = itemView.findViewById(R.id.scoreText)
        private val voteUpButton: ImageButton = itemView.findViewById(R.id.voteUpButton)
        private val voteDownButton: ImageButton = itemView.findViewById(R.id.voteDownButton)

        fun bind(comment: Comment) {
            usernameText.text = comment.creatorName ?: "User #${comment.creatorId}"
            commentBody.text = comment.body
            scoreText.text = comment.score.toString()
            
            // Format date
            comment.createdAt?.let { dateString ->
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
                    val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                    val date = inputFormat.parse(dateString)
                    dateText.text = date?.let { outputFormat.format(it) } ?: dateString
                } catch (e: Exception) {
                    dateText.text = dateString.take(10)
                }
            }

            // Score color
            val scoreColor = when {
                comment.score > 0 -> R.color.score_positive
                comment.score < 0 -> R.color.score_negative
                else -> R.color.text_secondary
            }
            scoreText.setTextColor(itemView.context.getColor(scoreColor))

            // Click listeners
            usernameText.setOnClickListener {
                comment.creatorName?.let { name -> onUserClick(name) }
            }
            
            voteUpButton.setOnClickListener { onVoteUp(comment) }
            voteDownButton.setOnClickListener { onVoteDown(comment) }
        }
    }

    private class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
            return oldItem == newItem
        }
    }
}
