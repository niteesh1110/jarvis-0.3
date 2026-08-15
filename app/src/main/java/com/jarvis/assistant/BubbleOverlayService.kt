package com.jarvis.assistant

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A small draggable floating bubble (like a chat head) that sits on top of
 * any app. Tap it for a one-shot voice command, drag it to reposition it.
 * Requires "Draw over other apps" permission, granted from MainActivity.
 */
class BubbleOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private var voiceManager: VoiceRecognitionManager? = null
    private var commandProcessor: CommandProcessor? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val apiKey = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
            .getString("claude_api_key", "") ?: ""
        commandProcessor = CommandProcessor(applicationContext, apiKey)

        bubbleView = TextView(this).apply {
            text = "J"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF"))
            }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            140, 140,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onBubbleTapped()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)

        voiceManager = VoiceRecognitionManager(
            context = applicationContext,
            onResult = { text -> handleHeard(text) },
            onError = { setBubbleColor("#00E5FF") }
        )
    }

    private fun onBubbleTapped() {
        setBubbleColor("#FF5252") // red while listening
        voiceManager?.startListening()
    }

    private fun handleHeard(text: String) {
        setBubbleColor("#FFC107") // amber while thinking
        CoroutineScope(Dispatchers.Main).launch {
            val reply = commandProcessor?.handle(text, null) ?: "Something went wrong."
            voiceManager?.speak(reply)
            setBubbleColor("#00E5FF") // back to idle cyan
        }
    }

    private fun setBubbleColor(hex: String) {
        (bubbleView.background as? GradientDrawable)?.setColor(Color.parseColor(hex))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        voiceManager?.destroy()
        if (::bubbleView.isInitialized) {
            try { windowManager.removeView(bubbleView) } catch (e: Exception) {}
        }
        super.onDestroy()
    }
}
