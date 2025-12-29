package com.e621.client.ui.browse

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.R
import com.e621.client.data.model.User
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter for displaying users in a RecyclerView
 */
class UserAdapter(
    private val onUserClick: (User) -> Unit
) : ListAdapter<User, UserAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtUserName: TextView = itemView.findViewById(R.id.txtUserName)
        private val txtUserLevel: TextView = itemView.findViewById(R.id.txtUserLevel)
        private val txtUserInfo: TextView = itemView.findViewById(R.id.txtUserInfo)
        
        fun bind(user: User) {
            txtUserName.text = user.name
            txtUserLevel.text = getLevelName(user.level ?: 20)
            
            val infoBuilder = StringBuilder()
            
            // Post count
            user.postUploadCount?.let {
                infoBuilder.append("$it posts")
            }
            
            // Join date
            user.createdAt?.let { dateStr ->
                try {
                    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val date = format.parse(dateStr)
                    val displayFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                    date?.let {
                        if (infoBuilder.isNotEmpty()) infoBuilder.append(" • ")
                        infoBuilder.append("Joined ${displayFormat.format(it)}")
                    }
                } catch (e: Exception) {
                    // Ignore date parsing errors
                }
            }
            
            txtUserInfo.text = if (infoBuilder.isNotEmpty()) infoBuilder.toString() else "User"
            
            itemView.setOnClickListener {
                onUserClick(user)
            }
        }
        
        private fun getLevelName(level: Int): String {
            return when (level) {
                10 -> "Blocked"
                20 -> "Member"
                30 -> "Privileged"
                31 -> "Contributor"
                32 -> "Former Staff"
                33 -> "Janitor"
                34 -> "Moderator"
                35 -> "Admin"
                else -> "Level $level"
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}
