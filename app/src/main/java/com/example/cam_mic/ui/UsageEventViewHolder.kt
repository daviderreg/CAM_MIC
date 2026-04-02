package com.example.cam_mic.ui

import androidx.recyclerview.widget.RecyclerView
import com.example.cam_mic.R
import com.example.cam_mic.data.ResourceType
import com.example.cam_mic.databinding.ItemUsageEventBinding

/**
 * ViewHolder for usage event items in the RecyclerView.
 */
class UsageEventViewHolder(
    private val binding: ItemUsageEventBinding
) : RecyclerView.ViewHolder(binding.root) {

    /**
     * Bind data to the view.
     */
    fun bind(event: com.example.cam_mic.data.UsageEventEntity) {
        // Set icon based on resource type
        val iconRes = when (event.resourceType) {
            ResourceType.AUDIO -> R.drawable.ic_microphone
            ResourceType.CAMERA -> R.drawable.ic_camera
        }
        binding.ivResourceType.setImageResource(iconRes)
        
        // Set resource type badge color
        val colorRes = when (event.resourceType) {
            ResourceType.AUDIO -> R.color.audio_badge
            ResourceType.CAMERA -> R.color.camera_badge
        }
        binding.tvResourceType.setBackgroundColor(itemView.context.getColor(colorRes))
        binding.tvResourceType.text = event.resourceType.name
        
        // Set package name
        binding.tvPackageName.text = event.packageName
        
        // Set timestamps
        binding.tvStartTime.text = event.startTimeMs.formatTime()
        binding.tvEndTime.text = event.endTimeMs?.formatTime() ?: "Ongoing"
        
        // Set duration
        binding.tvDuration.text = event.durationMs.formatDuration()
        
        // Set camera ID if applicable
        if (event.resourceType == ResourceType.CAMERA && event.cameraId != null) {
            binding.tvCameraId.visibility = android.view.View.VISIBLE
            binding.tvCameraId.text = "Camera ${event.cameraId}"
        } else {
            binding.tvCameraId.visibility = android.view.View.GONE
        }
        
        // Set ongoing status
        if (event.endTimeMs == null) {
            binding.tvStatus.visibility = android.view.View.VISIBLE
            binding.tvStatus.text = "● Active"
        } else {
            binding.tvStatus.visibility = android.view.View.GONE
        }
    }
}
