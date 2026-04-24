package com.example.askmechat.presentation.chat.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.askmechat.databinding.ItemSuggestionChipBinding
import androidx.core.graphics.toColorInt
import com.example.askmechat.R

class SuggestionChipAdapter(
    private val onSuggestionClick: (String) -> Unit,
    private val minCharsForSplit: Int = 20,
    private val maxDisplayChars: Int = 55
) : ListAdapter<String, SuggestionChipAdapter.SuggestionChipViewHolder>(SuggestionDiffUtil()) {

    private var isDisabled = false
    private var isShimmerActive = false

    /** Track which positions have already played their entrance animation. */
    private val animatedPositions = mutableSetOf<Int>()

    fun setDisabled(disabled: Boolean) {
        isDisabled = disabled
        notifyItemRangeChanged(0, itemCount)
    }

    fun setShimmerActive(active: Boolean) {
        isShimmerActive = active
        notifyItemRangeChanged(0, itemCount)
    }

    private fun formatSuggestion(text: String): String {
        val truncated = if (text.length > maxDisplayChars) {
            text.take(maxDisplayChars) + "..."
        } else text

        if (truncated.length <= minCharsForSplit) return truncated

        val builder = StringBuilder(truncated)
        val midpoint = truncated.length / 2
        val breakIndex = findBreakIndex(builder, midpoint)
        if (breakIndex < builder.length && builder[breakIndex] == ' ') {
            builder.setCharAt(breakIndex, '\n')
        } else {
            builder.insert(breakIndex, '\n')
        }
        return builder.toString()
    }

    private fun findBreakIndex(text: CharSequence, midpoint: Int): Int {
        if (midpoint >= text.length) return text.length

        var index = midpoint
        while (index < text.length) {
            if (text[index] == ' ') return index
            index++
        }
        index = midpoint
        while (index > 0) {
            if (text[index - 1] == ' ') return index - 1
            index--
        }
        return midpoint
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionChipViewHolder {
        val binding = ItemSuggestionChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SuggestionChipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionChipViewHolder, position: Int) {
        holder.bind(getItem(position))
        // Chips only fade in — no translate / scale — so their positions
        // are stable from the first frame. The continuous shimmer overlay
        // carries the "still loading" feel instead.
        fadeInIfNeeded(holder, position)
    }

    private fun fadeInIfNeeded(holder: SuggestionChipViewHolder, position: Int) {
        if (position in animatedPositions) return
        animatedPositions.add(position)

        val view = holder.itemView
        view.animate().cancel()
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(760L)
            .setStartDelay(50L * position.coerceAtMost(6))
            .start()
    }

    inner class SuggestionChipViewHolder(
        private val binding: ItemSuggestionChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: String) {
            binding.tvSuggestion.text = formatSuggestion(suggestion)
            binding.root.isEnabled = !isDisabled
            binding.root.alpha = if (isDisabled) 0.4f else 1.0f
            binding.tvSuggestion.setTextColor(
                if (isDisabled) "#455159".toColorInt()
                else binding.root.context.getResources().getColor(R.color.suggestion_color)
            )

            binding.viewShimmerOverlay.visibility =
                if (isShimmerActive && !isDisabled) View.VISIBLE else View.GONE

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * binding.root.resources.displayMetrics.density
                setColor(
                    android.graphics.Color.parseColor(
                        if (isDisabled) "#202832" else "#2C3740"
                    )
                )
            }
            binding.root.background = bg

            binding.root.setOnClickListener {
                if (!isDisabled) onSuggestionClick(suggestion)
            }
        }
    }
}

class SuggestionDiffUtil : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean =
        oldItem == newItem

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean =
        oldItem == newItem
}
