package com.pixelbot

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.sin

class PixelBotOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var mascot: MascotView? = null

    override fun onCreate() { super.onCreate(); windowManager = getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hideMascot()
            ACTION_SHOW -> showMascot()
            ACTION_NOTIFICATION -> { showMascot(); mascot?.setMood(if (intent.getStringExtra(EXTRA_SOURCE) == "vk") 1 else if (intent.getStringExtra(EXTRA_SOURCE) == "telegram") 2 else 0, true) }
            ACTION_ACTIVITY -> { showMascot(); mascot?.setActivity(intent.getStringExtra(EXTRA_MOOD) ?: "activity") }
            ACTION_SCREEN_STATE -> { showMascot(); mascot?.setScreenMood(intent.getStringExtra(EXTRA_MOOD) ?: "activity") }
        }
        return START_STICKY
    }

    private fun showMascot() {
        if (mascot != null || !android.provider.Settings.canDrawOverlays(this)) return
        val size = (176 * resources.displayMetrics.density).toInt()
        val view = MascotView()
        val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 8; y = 170 }
        view.setOnTouchListener(object : View.OnTouchListener {
            var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dx=e.rawX; dy=e.rawY; sx=params.x; sy=params.y; return true }
                    MotionEvent.ACTION_MOVE -> { params.x=(sx-(e.rawX-dx)).toInt(); params.y=(sy+(e.rawY-dy)).toInt(); windowManager.updateViewLayout(v,params); return true }
                }
                return true
            }
        })
        mascot = view
        isVisible = true
        windowManager.addView(view, params)
    }

    private fun hideMascot() {
        mascot?.let { runCatching { windowManager.removeView(it) } }
        mascot = null; isVisible = false
    }
    override fun onDestroy() { hideMascot(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    inner class MascotView : View(this@PixelBotOverlayService) {
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        private val idle = BitmapFactory.decodeResource(resources, R.drawable.mascot_sheet)
        private val reactions = BitmapFactory.decodeResource(resources, R.drawable.reactions_sheet)
        private var reaction = false
        private var reactionFrame = 0
        private var reactionUntil = 0L
        private var mood = "activity"
        private var tick = 0

        fun setMood(index: Int, notification: Boolean) {
            reaction = notification; reactionFrame = index.coerceIn(0,8)
            reactionUntil = System.currentTimeMillis() + 3500L; invalidate()
        }
        fun setActivity(kind: String) { mood = kind; invalidate() }
        fun setScreenMood(kind: String) { mood = kind; invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            tick++
            val now = System.currentTimeMillis()
            if (reaction && now > reactionUntil) reaction = false
            val bob = sin(tick / 8.0).toFloat() * 4f * resources.displayMetrics.density
            val size = width * 0.98f
            val left = (width-size)/2f
            val top = (height-size)/2f + bob
            val sheet = if (reaction) reactions else idle
            val cols = if (reaction) 3 else 4
            val cellW = sheet.width / cols
            val cellH = sheet.height / cols
            val frame = if (reaction) reactionFrame else when (mood) {
                "video" -> 5
                "game" -> 2
                "browse" -> 1
                "settings" -> 8
                "chat" -> 11
                "look" -> 0
                "scroll" -> 9
                "click" -> 1
                "app" -> 0
                else -> intArrayOf(0,1,5,9,11,15)[(tick/36)%6]
            }
            val src = android.graphics.Rect((frame%cols)*cellW, (frame/cols)*cellH, (frame%cols+1)*cellW, (frame/cols+1)*cellH)
            canvas.drawBitmap(sheet, src, android.graphics.RectF(left,top,left+size,top+size), paint)
            postInvalidateDelayed(90L)
        }
    }

    companion object {
        const val ACTION_SHOW="com.pixelbot.SHOW"
        const val ACTION_HIDE="com.pixelbot.HIDE"
        const val ACTION_NOTIFICATION="com.pixelbot.NOTIFICATION"
        const val ACTION_ACTIVITY="com.pixelbot.ACTIVITY"
        const val ACTION_SCREEN_STATE="com.pixelbot.SCREEN_STATE"
        const val EXTRA_SOURCE="source"
        const val EXTRA_MOOD="mood"
        var isVisible=false; private set
    }
}