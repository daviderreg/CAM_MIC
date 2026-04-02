package com.example.cam_mic.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.example.cam_mic.data.UsageEventEntity
import com.example.cam_mic.databinding.ItemUsageEventBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * RecyclerView adapter for displaying usage events.
 * Uses ListAdapter with DiffUtil for efficient updates.
 */
class UsageEventAdapter(
    private val onItemClick: ((UsageEventEntity) -> Unit)? = null
) : ListAdapter<UsageEventEntity, UsageEventViewHolder>(UsageEventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsageEventViewHolder {
        val binding = ItemUsageEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UsageEventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UsageEventViewHolder, position: Int) {
        val event = getItem(position)
        holder.bind(event)
        
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(event)
        }
    }

    /**
     * DiffUtil callback for efficient list updates.
     */
    private class UsageEventDiffCallback : DiffUtil.ItemCallback<UsageEventEntity>() {
        override fun areItemsTheSame(oldItem: UsageEventEntity, newItem: UsageEventEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UsageEventEntity, newItem: UsageEventEntity): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Extension function to format duration in human-readable format.
 */
fun Long.formatDuration(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes % 60, seconds % 60)
        minutes > 0 -> String.format(Locale.getDefault(), "%dm %ds", minutes, seconds % 60)
        else -> String.format(Locale.getDefault(), "%ds", seconds)
    }
}

/**
 * Extension function to format timestamp.
 */
fun Long.formatTimestamp(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(java.util.Date(this))
}

/**
 * Extension function to format time only.
 */
fun Long.formatTime(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(java.util.Date(this))
}
