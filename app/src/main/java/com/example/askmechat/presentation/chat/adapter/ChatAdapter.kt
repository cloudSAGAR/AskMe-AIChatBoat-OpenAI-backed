package com.example.askmechat.presentation.chat.adapter

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.askmechat.databinding.ItemChatAiBinding
import com.example.askmechat.databinding.ItemChatUserBinding
import com.example.askmechat.domain.model.ChatMessage
import com.example.askmechat.presentation.chat.widget.TypingCursorSpan

/**
 * Presentation-layer adapter that renders a [ChatMessage] list into the
 * RecyclerView. Owns the streaming "typing" animation, the breathing-dot
 * cursor and a small markdown-ish formatter for **bold** / headers / lists.
 */
class ChatAdapter(
    private val onAIMessageClick: ((ChatMessage.AIMessage) -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffUtil()) {

    private val animatedItems = mutableSetOf<String>()

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
        private const val BREATHING_DURATION_MS = 800L
        private const val TYPING_TICK_MS = 12L
        private const val CHARS_PER_TICK = 2
        private const val FIRST_ITEM_EXTRA_TOP_DP = 25
        private const val LAST_ITEM_EXTRA_BOTTOM_DP = 12
        private const val BOLD_DENSITY = 0.1f
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatMessage.UserMessage -> VIEW_TYPE_USER
        is ChatMessage.AIMessage -> VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> UserMessageViewHolder(
                ItemChatUserBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_AI -> AIMessageViewHolder(
                ItemChatAiBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message as ChatMessage.UserMessage)
            is AIMessageViewHolder -> holder.bind(message as ChatMessage.AIMessage, onAIMessageClick)
        }
        applyFirstItemTopMargin(holder, position)
        applyLastItemBottomMargin(holder, position)
        animateEntrance(holder, message)
    }

    private fun applyFirstItemTopMargin(holder: RecyclerView.ViewHolder, position: Int) {
        val lp = holder.itemView.layoutParams as? RecyclerView.LayoutParams ?: return
        if (holder.itemView.tag == null) {
            holder.itemView.tag = Pair(lp.topMargin, lp.bottomMargin)
        }
        val (baseTop, baseBottom) = holder.itemView.tag as Pair<Int, Int>
        val extra = if (position == 0) {
            (FIRST_ITEM_EXTRA_TOP_DP * holder.itemView.resources.displayMetrics.density).toInt()
        } else 0
        lp.topMargin = baseTop + extra
        lp.bottomMargin = baseBottom
        holder.itemView.layoutParams = lp
    }

    private fun applyLastItemBottomMargin(holder: RecyclerView.ViewHolder, position: Int) {
        val lp = holder.itemView.layoutParams as? RecyclerView.LayoutParams ?: return
        val (_, baseBottom) = holder.itemView.tag as? Pair<Int, Int> ?: Pair(0, 0)
        val extra = if (position == itemCount - 1) {
            (LAST_ITEM_EXTRA_BOTTOM_DP * holder.itemView.resources.displayMetrics.density).toInt()
        } else 0
        lp.bottomMargin = baseBottom + extra
        holder.itemView.layoutParams = lp
    }

    private fun animateEntrance(holder: RecyclerView.ViewHolder, message: ChatMessage) {
        if (message.id in animatedItems) return
        animatedItems.add(message.id)

        val view = holder.itemView
        val isUser = message is ChatMessage.UserMessage
        view.animate().cancel()

        view.alpha = 0f
        view.translationY = 80f
        view.translationX = if (isUser) 60f else -60f
        view.scaleX = 0.92f
        view.scaleY = 0.92f

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400L)
            .setInterpolator(DecelerateInterpolator(2.0f))
            .start()
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && holder is AIMessageViewHolder) {
            val message = getItem(position) as? ChatMessage.AIMessage ?: return
            holder.bind(message, onAIMessageClick)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationX = 0f
        holder.itemView.translationY = 0f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
        if (holder is AIMessageViewHolder) holder.cleanUp()
    }

    // ── User bubble ────────────────────────────────────────────────────

    class UserMessageViewHolder(
        private val binding: ItemChatUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage.UserMessage) {
            binding.tvUserMessage.text = message.text
        }
    }

    // ── AI bubble with streaming typing + markdown-lite ────────────────

    class AIMessageViewHolder(
        private val binding: ItemChatAiBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var breathingAnimator: ValueAnimator? = null
        private var cursorSpan: TypingCursorSpan? = null

        private val typingHandler = Handler(Looper.getMainLooper())
        private var targetText: String = ""
        private var displayedLength: Int = 0
        private var isTypingRunning = false

        private val typingRunnable = object : Runnable {
            override fun run() {
                if (displayedLength < targetText.length) {
                    displayedLength = (displayedLength + CHARS_PER_TICK)
                        .coerceAtMost(targetText.length)
                    renderTypingFrame()
                    typingHandler.postDelayed(this, TYPING_TICK_MS)
                } else {
                    isTypingRunning = false
                }
            }
        }

        fun bind(
            message: ChatMessage.AIMessage,
            onAIMessageClick: ((ChatMessage.AIMessage) -> Unit)? = null
        ) {
            bindStreaming(message)

            if (message.mapPoints != null && !message.isStreaming) {
                itemView.setOnClickListener { onAIMessageClick?.invoke(message) }
                itemView.isClickable = true
            } else {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }
        }

        fun bindStreaming(message: ChatMessage.AIMessage) {
            val isWaitingForFirstChunk =
                (message.isLoading || message.isStreaming) && message.text.isEmpty()
            val hasText = message.text.isNotEmpty()

            if (isWaitingForFirstChunk) {
                stopAllAnimations()
                binding.dotsLoading.visibility = View.VISIBLE
                binding.dotsLoading.startAnimating()
            } else {
                binding.dotsLoading.stopAnimating()
                binding.dotsLoading.visibility = View.GONE
            }

            if (hasText) {
                binding.tvAiMessage.visibility = View.VISIBLE
                if (message.isStreaming) {
                    onStreamingChunk(message.text)
                } else {
                    stopAllAnimations()
                    displayedLength = 0
                    targetText = ""
                    binding.tvAiMessage.text = formatMessageWithBold(message.text)
                }
            } else {
                stopAllAnimations()
                binding.tvAiMessage.visibility = View.GONE
                binding.tvAiMessage.text = ""
            }
        }

        private fun onStreamingChunk(fullText: String) {
            targetText = fullText
            startBreathingDot()
            if (!isTypingRunning) {
                isTypingRunning = true
                typingHandler.post(typingRunnable)
            }
        }

        private fun renderTypingFrame() {
            val visibleRawText = targetText.substring(0, displayedLength)
            val formattedText = formatMessageWithBold(visibleRawText)
            val builder = SpannableStringBuilder(formattedText)
            builder.append(" ")
            val span = cursorSpan ?: TypingCursorSpan(dotColor = Color.WHITE).also { cursorSpan = it }
            builder.setSpan(
                span,
                builder.length - 1,
                builder.length,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            binding.tvAiMessage.text = builder
        }

        private fun startBreathingDot() {
            if (breathingAnimator != null) return
            breathingAnimator = ValueAnimator.ofFloat(
                TypingCursorSpan.MIN_SCALE,
                TypingCursorSpan.MAX_SCALE
            ).apply {
                duration = BREATHING_DURATION_MS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val scale = animator.animatedValue as Float
                    cursorSpan?.scale = scale
                    binding.tvAiMessage.invalidate()
                }
                start()
            }
        }

        fun cleanUp() { stopAllAnimations() }

        private fun stopAllAnimations() {
            breathingAnimator?.cancel()
            breathingAnimator = null
            cursorSpan = null
            typingHandler.removeCallbacks(typingRunnable)
            isTypingRunning = false
        }

        private fun formatMessageWithBold(text: String): SpannableString {
            data class SpanInfo(val start: Int, val end: Int, val sizeMultiplier: Float)

            val spans = mutableListOf<SpanInfo>()
            var processedText = text

            processedText = processedText.replace(Regex("""\n{3,}"""), "\n\n")
            processedText = processedText.replace(Regex("""\n[ \t]+"""), "\n")

            processedText = processedText.replace(
                Regex("""(\d+\.\s+)([^*]+?)\s+-\s+([^\n]+)""")
            ) { matchResult ->
                val number = matchResult.groupValues[1]
                val title = matchResult.groupValues[2].trim()
                val description = matchResult.groupValues[3].trim()
                "$number$title\n- $description\n"
            }

            processedText = processedText.replace(Regex("""\n{3,}"""), "\n\n")

            processedText = processedText.replace(
                Regex("""((?:\*\*\d+\.\s+[^*]+\*\*)|(?:\d+\.\s+[^*\n]*\*\*[^*]+\*\*))\s*-\s*([^\n]+)(\n|$)""")
            ) { matchResult ->
                val titlePart = matchResult.groupValues[1]
                val location = matchResult.groupValues[2].trim()
                "$titlePart \n\n- $location\n\n"
            }

            processedText = processedText.replace(Regex("""\n{3,}"""), "\n\n")
            processedText = processedText.replace(Regex("""(?m)^#{1,3}\s*"""), "")
            processedText = processedText.replace(Regex("""(?<!\n)\n- """), "\n\n- ")
            processedText = processedText.replace(Regex("""\n+(\d+\.)"""), "\n\n$1")
            processedText = processedText.replace(Regex("""\n\n(\*\*[^*]+:\*\*)"""), "\n\n\n$1")

            val boldPattern = Regex("""\*\*(.+?)\*\*""")
            var boldMatch = boldPattern.find(processedText)
            while (boldMatch != null) {
                val content = boldMatch.groupValues[1]
                val startPos = boldMatch.range.first
                processedText = processedText.substring(0, startPos) +
                    content +
                    processedText.substring(boldMatch.range.last + 1)
                spans.add(SpanInfo(startPos, startPos + content.length, 1.0f))
                boldMatch = boldPattern.find(processedText)
            }

            val spannableString = SpannableString(processedText)
            spans.forEach { info ->
                val safeStart = info.start.coerceIn(0, processedText.length)
                val safeEnd = info.end.coerceIn(safeStart, processedText.length)
                spannableString.setSpan(
                    ExtraBoldSpan(),
                    safeStart, safeEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (info.sizeMultiplier != 1.0f) {
                    spannableString.setSpan(
                        RelativeSizeSpan(info.sizeMultiplier),
                        safeStart, safeEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            return spannableString
        }

        private inner class ExtraBoldSpan : MetricAffectingSpan() {
            override fun updateDrawState(paint: TextPaint) = apply(paint)
            override fun updateMeasureState(paint: TextPaint) = apply(paint)
            private fun apply(paint: TextPaint) {
                paint.typeface = Typeface.DEFAULT_BOLD
                if (BOLD_DENSITY > 0f) {
                    paint.style = android.graphics.Paint.Style.FILL_AND_STROKE
                    paint.strokeWidth = BOLD_DENSITY
                }
            }
        }
    }
}
