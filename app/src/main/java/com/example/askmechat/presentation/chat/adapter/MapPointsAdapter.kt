package com.example.askmechat.presentation.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.askmechat.databinding.ItemMapExpandButtonBinding
import com.example.askmechat.databinding.ItemMapPointBinding
import com.example.askmechat.domain.model.MapPoint

/**
 * Horizontally-scrolling list of map points appended to an AI answer when
 * the backend returns a visualization block.
 *
 * Item at position 0 is the "expand to full-screen map" button; subsequent
 * items are the actual data points.
 */
class MapPointsAdapter(
    private val onMapExpandClick: () -> Unit,
    private val onPointClick: (MapPoint, Int) -> Unit
) : ListAdapter<MapPoint, RecyclerView.ViewHolder>(MapPointDiffUtil()) {

    companion object {
        private const val VIEW_TYPE_MAP_EXPAND = 0
        private const val VIEW_TYPE_POINT = 1
    }

    override fun getItemCount(): Int = super.getItemCount() + 1 // +1 expand button

    override fun getItemViewType(position: Int): Int =
        if (position == 0) VIEW_TYPE_MAP_EXPAND else VIEW_TYPE_POINT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MAP_EXPAND -> MapExpandViewHolder(
                ItemMapExpandButtonBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
            else -> MapPointViewHolder(
                ItemMapPointBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MapExpandViewHolder -> holder.bind()
            is MapPointViewHolder -> {
                val dataIndex = position - 1
                holder.bind(getItem(dataIndex), dataIndex)
            }
        }
    }

    inner class MapExpandViewHolder(
        private val binding: ItemMapExpandButtonBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener { onMapExpandClick() }
        }
    }

    inner class MapPointViewHolder(
        private val binding: ItemMapPointBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MapPoint, index: Int) {
            binding.tvMapNumber.text = (index + 1).toString()
            binding.tvMapPointName.text =
                if (item.name.length > 40) item.name.take(40) + "..." else item.name
            binding.tvMapPointAddress.text =
                if (item.address.length > 40) item.address.take(40) + "..." else item.address
            binding.root.setOnClickListener { onPointClick(item, index) }
        }
    }
}

class MapPointDiffUtil : DiffUtil.ItemCallback<MapPoint>() {
    override fun areItemsTheSame(oldItem: MapPoint, newItem: MapPoint): Boolean =
        oldItem.name == newItem.name && oldItem.lat == newItem.lat && oldItem.lng == newItem.lng

    override fun areContentsTheSame(oldItem: MapPoint, newItem: MapPoint): Boolean =
        oldItem == newItem
}
