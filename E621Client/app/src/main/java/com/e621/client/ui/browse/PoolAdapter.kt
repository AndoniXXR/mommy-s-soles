package com.e621.client.ui.browse

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.R
import com.e621.client.data.model.Pool

/**
 * Adapter for displaying pools in a RecyclerView
 */
class PoolAdapter(
    private val onPoolClick: (Pool) -> Unit
) : ListAdapter<Pool, PoolAdapter.PoolViewHolder>(PoolDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoolViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pool, parent, false)
        return PoolViewHolder(view)
    }

    override fun onBindViewHolder(holder: PoolViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PoolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtPoolName: TextView = itemView.findViewById(R.id.txtPoolName)
        private val txtPoolDescription: TextView = itemView.findViewById(R.id.txtPoolDescription)
        private val txtPoolInfo: TextView = itemView.findViewById(R.id.txtPoolInfo)
        
        fun bind(pool: Pool) {
            txtPoolName.text = pool.name?.replace("_", " ") ?: "Pool #${pool.id}"
            
            val description = pool.description?.take(150) ?: ""
            if (description.isNotEmpty()) {
                txtPoolDescription.text = description
                txtPoolDescription.visibility = View.VISIBLE
            } else {
                txtPoolDescription.visibility = View.GONE
            }
            
            val postCount = pool.postCount ?: pool.postIds?.size ?: 0
            val category = pool.category ?: "collection"
            txtPoolInfo.text = "$postCount posts • ${category.replaceFirstChar { it.uppercase() }}"
            
            itemView.setOnClickListener {
                onPoolClick(pool)
            }
        }
    }

    class PoolDiffCallback : DiffUtil.ItemCallback<Pool>() {
        override fun areItemsTheSame(oldItem: Pool, newItem: Pool): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Pool, newItem: Pool): Boolean {
            return oldItem == newItem
        }
    }
}
