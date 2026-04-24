package com.example.askmechat.presentation.chat

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.askmechat.databinding.FragmentChatBinding
import com.example.askmechat.domain.model.MapPoint
import com.example.askmechat.presentation.chat.adapter.ChatAdapter
import com.example.askmechat.presentation.chat.adapter.MapPointsAdapter
import com.example.askmechat.presentation.chat.adapter.SuggestionChipAdapter
import com.example.askmechat.presentation.chat.state.ChatScreenState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Single-screen chat Fragment. Renders a [ChatScreenState] coming from
 * [ChatViewModel] and relays user intents back via public methods.
 *
 * Keyboard-aware behaviour, auto-scroll during streaming and map-point
 * slide-in animation live here because they are pure View-layer concerns.
 */
class ChatBoatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(requireActivity().application)
    }

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var suggestionAdapter: SuggestionChipAdapter
    private lateinit var mapPointsAdapter: MapPointsAdapter

    private var lastMessageCount = 0
    private var currentDisplayedMapData: List<MapPoint>? = null
    private var hasMapViewAnimated = false
    private var isKeyboardVisible = false
    private var rvOriginalBottomMargin = Int.MIN_VALUE
    private var lastKeyboardVisibleTs = 0L
    private var userScrolledAfterSend = false
    private var adapterDataObserver: RecyclerView.AdapterDataObserver? = null

    /** Scale-pulse animator for the send button; null when idle. */
    private var sendPulseAnimator: android.animation.ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        @Suppress("DEPRECATION")
        requireActivity().window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        setupRecyclerViews()
        setupClickListeners()
        setupInputListener()
        setupKeyboardInsetListener()
        setupBackPressHandler()
        observeState()
        startSuggestionShimmer()
        playHeroIntroAnimations()
        startSparkleIdleAnimation()
    }

    /**
     * Staggered fade+slide-in for the empty-state hero. The title has
     * already started shifting its gradient by the time the subtitle
     * settles, which gives the screen a lively, "awakening" feel.
     */
    private fun playHeroIntroAnimations() {
        val anim = android.view.animation.AnimationUtils
            .loadAnimation(requireContext(), com.example.askmechat.R.anim.fade_slide_in)
        fun apply(view: View, startOffsetMs: Long) {
            view.alpha = 0f
            view.postDelayed({
                if (_binding == null) return@postDelayed
                view.alpha = 1f
                view.startAnimation(
                    android.view.animation.AnimationUtils
                        .loadAnimation(requireContext(), com.example.askmechat.R.anim.fade_slide_in)
                )
            }, startOffsetMs)
        }
        apply(binding.ivIntroSparkle, 100)
        apply(binding.tvChatIntroTitle, 260)
        apply(binding.tvChatIntroSubtitle, 420)
        apply(binding.tvChatIntroFootnote, 560)
    }

    /**
     * Gentle idle motion on the AI sparkle — a slow rotation plus a
     * breathing scale. Both are infinite and cancelled with the view.
     */
    private fun startSparkleIdleAnimation() {
        val sparkle = binding.ivIntroSparkle
        // Slow rotation
        sparkle.animate().cancel()
        val rotator = android.animation.ObjectAnimator.ofFloat(sparkle, View.ROTATION, 0f, 360f)
            .apply {
                duration = 9000L
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                start()
            }
        // Breathing scale
        val breath = android.animation.ValueAnimator.ofFloat(1.0f, 1.08f).apply {
            duration = 1600L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                sparkle.scaleX = s
                sparkle.scaleY = s
            }
            start()
        }
        // Bind animator lifecycle to the fragment view
        sparkle.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                rotator.cancel()
                breath.cancel()
            }
        })
    }

    // ── State observation ─────────────────────────────────────────────

    private fun observeState() {
        // Keep list pinned to bottom during streaming (but respect user scroll)
        adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                if (_binding == null) return
                if (userScrolledAfterSend) return
                val lm = binding.rvChatMessages.layoutManager as? LinearLayoutManager ?: return
                val last = lm.findLastVisibleItemPosition()
                if (last >= chatAdapter.itemCount - 2) {
                    binding.rvChatMessages.post {
                        if (_binding != null) binding.rvChatMessages.scrollBy(0, Int.MAX_VALUE)
                    }
                }
            }
        }
        chatAdapter.registerAdapterDataObserver(adapterDataObserver!!)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ChatScreenState) {
        // Messages
        val newCount = state.messages.size
        val wasNewMessageAdded = newCount > lastMessageCount
        lastMessageCount = newCount

        chatAdapter.submitList(state.messages) {
            if (wasNewMessageAdded && state.messages.isNotEmpty()) {
                binding.rvChatMessages.scrollToPosition(state.messages.size - 1)
            }
        }
        binding.tvChatIntro.visibility = if (state.hasMessages) View.GONE else View.VISIBLE

        // Suggestions — chip strip is only meaningful in the empty state,
        // so keep it visible alongside the hero and hide it once any
        // message has been exchanged.
        suggestionAdapter.submitList(state.suggestions)
        binding.rvSuggestionChips.visibility =
            if (state.hasMessages) View.GONE else View.VISIBLE

        // Send button / input enable-state
        val canSend = !state.isSending && binding.etMessageInput.text?.isNotBlank() == true
        binding.ivSend.isEnabled = canSend
        binding.etMessageInput.isEnabled = !state.isSending
        updateSendButtonPulse(canSend)

        // Map points
        val points = state.mapPoints?.points
        if (!points.isNullOrEmpty()) {
            currentDisplayedMapData = points
            mapPointsAdapter.submitList(points) {
                binding.rvMapviewPoints.scrollToPosition(0)
            }
            if (!hasMapViewAnimated && binding.rvMapviewPoints.visibility != View.VISIBLE) {
                animateMapViewSlideIn()
                hasMapViewAnimated = true
            } else {
                binding.rvMapviewPoints.visibility = View.VISIBLE
            }
        } else {
            binding.rvMapviewPoints.visibility = View.GONE
            currentDisplayedMapData = null
        }

        // Progress bar
        if (state.isResponseComplete) binding.llProgressBar.visibility = View.GONE
    }

    // ── UI setup ──────────────────────────────────────────────────────

    private fun setupRecyclerViews() {
        chatAdapter = ChatAdapter(
            onAIMessageClick = { aiMessage ->
                viewModel.selectMapGroupForMessage(aiMessage.id)
            }
        )
        binding.rvChatMessages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        userScrolledAfterSend = true
                    }
                }
            })
        }

        suggestionAdapter = SuggestionChipAdapter(
            onSuggestionClick = { suggestion ->
                binding.etMessageInput.setText(suggestion)
                binding.etMessageInput.setSelection(suggestion.length)
                // Tapping a chip focuses the input as well — stop shimmer.
                suggestionAdapter.setShimmerActive(false)
            }
        )
        binding.rvSuggestionChips.apply {
            // Flex-wrap layout — chips wrap to multiple rows instead of
            // being clipped by a horizontal scroll edge. Justify CENTER so
            // short rows sit balanced on screen.
            layoutManager = com.google.android.flexbox.FlexboxLayoutManager(context).apply {
                flexDirection = com.google.android.flexbox.FlexDirection.ROW
                flexWrap = com.google.android.flexbox.FlexWrap.WRAP
                justifyContent = com.google.android.flexbox.JustifyContent.CENTER
            }
            adapter = suggestionAdapter
        }

        mapPointsAdapter = MapPointsAdapter(
            onMapExpandClick = { openMapView(selectedIndex = -1) },
            onPointClick = { _, index -> openMapView(selectedIndex = index) }
        )
        binding.rvMapviewPoints.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = mapPointsAdapter
        }
    }

    private fun setupClickListeners() {
        binding.includeToolbar.ivClose.setOnClickListener { goBack() }
        binding.ivSend.setOnClickListener { sendMessage() }
    }

    private fun setupInputListener() {
        binding.etMessageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.isNotBlank() == true
                binding.ivSend.isEnabled = hasText && !viewModel.state.value.isSending
                suggestionAdapter.setDisabled(hasText)
            }
        })
    }

    private fun setupKeyboardInsetListener() {
        val decorView = requireActivity().window.decorView
        ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
            val imeInset = insets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardHeight = imeInset.bottom
            val keyboardNowVisible = keyboardHeight > 0

            if (keyboardNowVisible) {
                lastKeyboardVisibleTs = android.os.SystemClock.elapsedRealtime()
            }
            isKeyboardVisible = keyboardNowVisible

            binding.llChatBottomPanel.translationY = -keyboardHeight.toFloat()

            val params = binding.rvChatMessages.layoutParams
                as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (rvOriginalBottomMargin == Int.MIN_VALUE) {
                rvOriginalBottomMargin = params.bottomMargin
            }
            params.bottomMargin = if (keyboardHeight > 0) {
                keyboardHeight + rvOriginalBottomMargin
            } else rvOriginalBottomMargin
            binding.rvChatMessages.layoutParams = params
            insets
        }
        ViewCompat.requestApplyInsets(decorView)
    }

    private fun removeKeyboardInsetListener() {
        activity?.window?.decorView?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it, null)
            ViewCompat.requestApplyInsets(it)
        }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (shouldConsumeBackForKeyboard()) {
                dismissKeyboardAndStay()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.etMessageInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                event.action == android.view.KeyEvent.ACTION_UP
            ) {
                if (shouldConsumeBackForKeyboard()) {
                    dismissKeyboardAndStay()
                    true
                } else false
            } else false
        }
    }

    private fun isKeyboardVisibleByHeight(): Boolean {
        val rootView = requireActivity().window.decorView.rootView
        val rect = android.graphics.Rect()
        rootView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = rootView.height
        val keypadHeight = screenHeight - rect.bottom
        return keypadHeight > screenHeight * 0.15
    }

    private fun shouldConsumeBackForKeyboard(): Boolean {
        if (isKeyboardVisible) return true
        if (isKeyboardVisibleByHeight()) return true
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastKeyboardVisibleTs
        return lastKeyboardVisibleTs > 0 && elapsed < KEYBOARD_GRACE_MS
    }

    private fun dismissKeyboardAndStay() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etMessageInput.windowToken, 0)
        binding.etMessageInput.clearFocus()
        isKeyboardVisible = false
        lastKeyboardVisibleTs = 0L
    }

    /**
     * Continuous shimmer — runs in a loop until the user focuses the
     * input box (tap / long-press on the EditText) or taps a chip.
     * The shimmer view itself does the ~1.2s sweep on repeat; this
     * adapter flag just toggles visibility.
     */
    private fun startSuggestionShimmer() {
        suggestionAdapter.setShimmerActive(true)

        binding.etMessageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) suggestionAdapter.setShimmerActive(false)
        }
        // If the user taps the field without it stealing focus (edge cases
        // on some keyboards), stop the shimmer on first touch too.
        binding.etMessageInput.setOnClickListener {
            suggestionAdapter.setShimmerActive(false)
        }
    }

    /**
     * Runs / stops a subtle breathing pulse on the send button whenever
     * it becomes enabled. Gives visual feedback that the input is ready
     * to submit without being distracting.
     */
    private fun updateSendButtonPulse(enabled: Boolean) {
        if (enabled) {
            if (sendPulseAnimator?.isRunning == true) return
            sendPulseAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 1.08f).apply {
                duration = 900L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val s = it.animatedValue as Float
                    if (_binding != null) {
                        binding.ivSend.scaleX = s
                        binding.ivSend.scaleY = s
                    }
                }
                start()
            }
        } else {
            sendPulseAnimator?.cancel()
            sendPulseAnimator = null
            if (_binding != null) {
                binding.ivSend.scaleX = 1f
                binding.ivSend.scaleY = 1f
            }
        }
    }

    // ── Actions ────────────────────────────────────────────────────────

    private fun sendMessage() {
        val prompt = binding.etMessageInput.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) return

        userScrolledAfterSend = false
        binding.rvSuggestionChips.visibility = View.GONE
        binding.etMessageInput.text?.clear()
        dismissKeyboardAndStay()

        viewModel.sendMessage(
            prompt = prompt,
            deviceId = "askmechat-${UUID.randomUUID()}",
            userCity = "",
            userId = null
        )
    }

    private fun openMapView(selectedIndex: Int) {
        val data = currentDisplayedMapData ?: return
        if (data.isEmpty()) return
        // TODO: launch your own full-screen map screen here.
        val picked = if (selectedIndex >= 0) data[selectedIndex].name else "all points"
        Toast.makeText(requireContext(), "Open map view → $picked", Toast.LENGTH_SHORT).show()
    }

    private fun goBack() {
        if (!isAdded) return
        dismissKeyboardAndStay()
        requireActivity().finish()
    }

    private fun animateMapViewSlideIn() {
        binding.rvMapviewPoints.apply {
            translationY = 200f
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        requireActivity().window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        viewModel.retryIfNeeded()
    }

    override fun onPause() {
        @Suppress("DEPRECATION")
        requireActivity().window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        super.onPause()
    }

    override fun onDestroyView() {
        adapterDataObserver?.let { chatAdapter.unregisterAdapterDataObserver(it) }
        adapterDataObserver = null
        sendPulseAnimator?.cancel()
        sendPulseAnimator = null
        userScrolledAfterSend = false
        lastMessageCount = 0
        hasMapViewAnimated = false
        currentDisplayedMapData = null
        removeKeyboardInsetListener()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val KEYBOARD_GRACE_MS = 500L
        fun newInstance() = ChatBoatFragment()
    }
}
