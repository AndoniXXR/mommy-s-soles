package com.e621.client.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.R
import com.e621.client.utils.LogEntry
import com.e621.client.utils.LogType

/**
 * Adapter for displaying log entries
 */
class LogAdapter(
    private val logs: List<LogEntry>
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgIcon: ImageView = itemView.findViewById(R.id.imgIcon)
        private val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        private val txtMessage: TextView = itemView.findViewById(R.id.txtMessage)
        private val txtTag: TextView = itemView.findViewById(R.id.txtTag)

        fun bind(entry: LogEntry) {
            txtTime.text = entry.getShortTime()
            txtMessage.text = entry.message
            
            if (entry.tag != null) {
                txtTag.visibility = View.VISIBLE
                txtTag.text = entry.tag
            } else {
                txtTag.visibility = View.GONE
            }
            
            // Set icon and color based on log type
            val (iconRes, colorRes) = when (entry.type) {
                LogType.INFO -> R.drawable.ic_info to android.R.color.darker_gray
                LogType.SUCCESS -> R.drawable.ic_check_circle to android.R.color.holo_green_dark
                LogType.WARNING -> R.drawable.ic_warning to android.R.color.holo_orange_dark
                LogType.ERROR -> R.drawable.ic_error to android.R.color.holo_red_dark
                LogType.CHECK -> R.drawable.ic_search to android.R.color.holo_blue_dark
                LogType.NOTIFICATION -> R.drawable.ic_notifications_active to android.R.color.holo_purple
            }
            
            imgIcon.setImageResource(iconRes)
            imgIcon.setColorFilter(ContextCompat.getColor(itemView.context, colorRes))
        }
    }
}
