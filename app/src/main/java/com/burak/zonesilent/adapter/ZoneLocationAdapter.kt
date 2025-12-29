package com.burak.zonesilent.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.burak.zonesilent.data.ZoneLocation
import com.burak.zonesilent.databinding.ItemZoneLocationBinding

class ZoneLocationAdapter(
    private val onDeleteClick: (ZoneLocation) -> Unit
) : ListAdapter<ZoneLocation, ZoneLocationAdapter.ZoneViewHolder>(ZoneDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZoneViewHolder {
        val binding = ItemZoneLocationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ZoneViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ZoneViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ZoneViewHolder(
        private val binding: ItemZoneLocationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(location: ZoneLocation) {
            binding.zoneName.text = location.name
            val modeText = if (location.mode == "SILENT") "Sessiz" else "Titreşim"
            binding.zoneDetails.text = "Radius: ${location.radius.toInt()}m • $modeText"
            
            binding.deleteButton.setOnClickListener {
                onDeleteClick(location)
            }
        }
    }

    private class ZoneDiffCallback : DiffUtil.ItemCallback<ZoneLocation>() {
        override fun areItemsTheSame(oldItem: ZoneLocation, newItem: ZoneLocation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ZoneLocation, newItem: ZoneLocation): Boolean {
            return oldItem == newItem
        }
    }
}
