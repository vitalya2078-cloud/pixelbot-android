package com.pixelbot

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.sin

class PixelBotOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var mascot: MascotView? = null
    private val handler = Handler(Looper.getMainLooper())
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
                currentSource = intent.getStringExtra(EXTRA_SOURCE) ?: "other"
                showMascot()
                mascot?.setReaction(currentSource)
            }
        }
        return START_NOT_STICKY
    }

    private fun showMascot() {
        if (mascot != null || !android.provider.Settings.canDrawOverlays(this)) return
        // Larger companion: 148 dp, with the sprite filling most of the transparent canvas.
        val size = (148 * resources.displayMetrics.density).toInt()
        val view = MascotView(this)
        val layoutParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
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
                        downX = event.rawX; downY = event.rawY
                        startX = layoutParams.x; startY = layoutParams.y
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
        isVisible = true
        windowManager.addView(view, layoutParams)
    }

    private fun hideMascot() {
        mascot?.let { runCatching { windowManager.removeView(it) } }
        mascot = null
        isVisible = false
    }

    override fun onDestroy() {
        hideMascot()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    inner class MascotView(context: android.content.Context) : View(context) {
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        private val mascotSheet: Bitmap? = loadSheet("mascot_sheet")
        private val reactionSheet: Bitmap? = loadSheet("reactions_sheet")
        private var reactionIndex = -1
        private var reactionUntil = 0L
        private var tick = 0

        private fun loadSheet(name: String): Bitmap? {
            val id = resources.getIdentifier(name, "drawable", packageName)
            return if (id != 0) BitmapFactory.decodeResource(resources, id) else null
        }

        fun setReaction(source: String) {
            reactionIndex = when (source) {
                "vk" -> 1
                "telegram" -> 2
                else -> 0 // Generic notification reaction for every other app.
            }
            reactionUntil = System.currentTimeMillis() + 3200L
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            tick++
            val now = System.currentTimeMillis()
            val useReaction = reactionIndex >= 0 && now < reactionUntil
            val density = resources.displayMetrics.density
            val bob = sin(tick / 7.0).toFloat() * 4.5f * density
            val drawSize = width * 0.98f
            val top = (height - drawSize) / 2f + bob
            val left = (width - drawSize) / 2f

            val sheet = if (useReaction) reactionSheet else mascotSheet
            if (sheet != null) {
                val columns = if (useReaction) 3 else 4
                val rows = if (useReaction) 3 else 4
                val cellW = sheet.width / columns
                val cellH = sheet.height / rows
                val index = if (useReaction) reactionIndex else {
                    // Rotate through distinct idle expressions to make the mascot feel alive.
                    val idleFrames = intArrayOf(0, 1, 4, 5, 8, 9, 11, 15)
                    idleFrames[(tick / 34) % idleFrames.size]
                }
                val col = index % columns
                val row = index / columns
                val src = android.graphics.Rect(col * cellW, row * cellH, (col + 1) * cellW, (row + 1) * cellH)
                val dst = android.graphics.RectF(left, top, left + drawSize, top + drawSize)
                canvas.drawBitmap(sheet, src, dst, paint)
            } else {
                // Fallback remains until the sprite PNG resources are included in the APK.
                val scale = drawSize / 64f
                canvas.save()
                canvas.translate(left, top)
                canvas.scale(scale, scale)
                fun block(color: Int, l: Float, t: Float, r: Float, b: Float) {
                    paint.color = color
                    canvas.drawRect(l, t, r, b, paint)
                }
                val outline = android.graphics.Color.rgb(55, 24, 20)
                val orange = android.graphics.Color.rgb(248, 126, 67)
                block(outline, 9f, 12f, 55f, 49f)
                block(orange, 13f, 15f, 51f, 46f)
                block(orange, 5f, 23f, 59f, 39f)
                block(outline, 15f, 45f, 23f, 55f)
                block(outline, 27f, 45f, 35f, 55f)
                block(outline, 39f, 45f, 47f, 55f)
                block(outline, 22f, 24f, 26f, 31f)
                block(outline, 38f, 24f, 42f, 31f)
                block(outline, 29f, 34f, 35f, 37f)
                canvas.restore()
            }
            if (!useReaction && reactionIndex >= 0 && now >= reactionUntil) reactionIndex = -1
            postInvalidateDelayed(70L)
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
