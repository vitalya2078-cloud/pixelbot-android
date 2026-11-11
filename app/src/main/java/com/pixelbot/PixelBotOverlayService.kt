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
    private var params: WindowManager.LayoutParams? = null
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
                currentSource = intent.getStringExtra(EXTRA_SOURCE) ?: "notification"
                showMascot()
                mascot?.setReaction(currentSource)
            }
        }
        return START_NOT_STICKY
    }

    private fun showMascot() {
        if (mascot != null || !android.provider.Settings.canDrawOverlays(this)) return
        val size = (112 * resources.displayMetrics.density).toInt()
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

    inner class MascotView(context: android.content.Context) : View(context) {
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        private val mascotSheet: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.mascot_sheet)
        private val reactionSheet: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.reactions_sheet)
        private var reactionIndex = -1
        private var reactionUntil = 0L
        private var tick = 0
        private val frameSize = mascotSheet.width / 4

        fun setReaction(source: String) {
            reactionIndex = when (source) {
                "vk" -> 1
                "telegram" -> 2
                else -> 0
            }
            reactionUntil = System.currentTimeMillis() + 2600L
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            tick++
            val now = System.currentTimeMillis()
            val useReaction = reactionIndex >= 0 && now < reactionUntil
            val bob = (sin(tick / 7.0) * 3.5f * resources.displayMetrics.density)
            val target = (width * 0.94f)
            val drawSize = target
            val top = (height - drawSize) / 2f + bob
            val left = (width - drawSize) / 2f

            val sheet: Bitmap
            val cell: Int
            val index: Int
            if (useReaction) {
                sheet = reactionSheet
                cell = sheet.width / 3
                index = reactionIndex
            } else {
                sheet = mascotSheet
                cell = frameSize
                // Alternate idle and happy frames while gently floating.
                index = if ((tick / 10) % 5 == 0) 1 else 0
            }
            val col = index % if (useReaction) 3 else 4
            val row = index / if (useReaction) 3 else 4
            val src = android.graphics.Rect(col * cell, row * cell, (col + 1) * cell, (row + 1) * cell)
            val dst = android.graphics.RectF(left, top, left + drawSize, top + drawSize)
            canvas.drawBitmap(sheet, src, dst, paint)
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