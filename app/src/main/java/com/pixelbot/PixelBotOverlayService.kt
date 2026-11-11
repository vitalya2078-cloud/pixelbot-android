package com.pixelbot

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.graphics.Color
import android.view.MotionEvent

class PixelBotOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var mascot: MascotView? = null
    private var params: WindowManager.LayoutParams? = null
    private var currentSource: String = "idle"

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hideMascot()
            ACTION_SHOW -> { currentSource = "idle"; showMascot() }
            ACTION_NOTIFICATION -> {
                currentSource = intent.getStringExtra(EXTRA_SOURCE) ?: "notification"
                showMascot()
                mascot?.invalidate()
            }
        }
        return START_NOT_STICKY
    }

    private fun showMascot() {
        if (mascot != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val view = MascotView(this)
        val layoutParams = WindowManager.LayoutParams(
            64, 64,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 180
        }
        view.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = layoutParams.x
                        startY = layoutParams.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = (startX - (event.rawX - downX)).toInt()
                        layoutParams.y = (startY + (event.rawY - downY)).toInt()
                        windowManager.updateViewLayout(v, layoutParams)
                        return true
                    }
                }
                return true
            }
        })
        mascot = view
        params = layoutParams
        isVisible = true
        windowManager.addView(view, layoutParams)
    }

    private fun hideMascot() {
        mascot?.let { runCatching { windowManager.removeView(it) } }
        mascot = null
        params = null
        isVisible = false
    }

    override fun onDestroy() {
        hideMascot()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    class MascotView(context: android.content.Context) : View(context) {
        private val paint = Paint().apply { isAntiAlias = false }
        private val service get() = context as PixelBotOverlayService
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val s = width / 64f
            canvas.save()
            canvas.scale(s, s)
            fun block(color: Int, l: Float, t: Float, r: Float, b: Float) {
                paint.color = color
                canvas.drawRect(l, t, r, b, paint)
            }
            val outline = Color.rgb(76, 35, 27)
            val orange = Color.rgb(245, 125, 65)
            val shade = Color.rgb(207, 78, 43)
            // Compact wide creature silhouette, not a star or robot.
            block(outline, 13f, 12f, 51f, 48f)
            block(outline, 8f, 20f, 56f, 40f)
            block(orange, 15f, 14f, 49f, 46f)
            block(orange, 10f, 22f, 54f, 38f)
            block(shade, 15f, 40f, 49f, 46f)
            // Four tiny feet.
            block(outline, 17f, 46f, 24f, 54f)
            block(outline, 27f, 46f, 34f, 54f)
            block(outline, 37f, 46f, 44f, 54f)
            block(outline, 47f, 46f, 52f, 54f)
            // Face.
            block(outline, 22f, 24f, 26f, 30f)
            block(outline, 38f, 24f, 42f, 30f)
            block(outline, 29f, 34f, 35f, 37f)
            // Tiny source marker: blue for Telegram, violet for VK, no message data.
            val marker = when (service.currentSource) {
                "telegram" -> Color.rgb(45, 155, 225)
                "vk" -> Color.rgb(110, 105, 220)
                else -> Color.TRANSPARENT
            }
            if (marker != Color.TRANSPARENT) {
                block(outline, 48f, 7f, 61f, 20f)
                block(marker, 50f, 9f, 59f, 18f)
            }
            canvas.restore()
        }
    }

    companion object {
        const val ACTION_SHOW = "com.pixelbot.SHOW"
        const val ACTION_HIDE = "com.pixelbot.HIDE"
        const val ACTION_NOTIFICATION = "com.pixelbot.NOTIFICATION"
        const val EXTRA_SOURCE = "source"
        var isVisible = false
            private set
    }
}
