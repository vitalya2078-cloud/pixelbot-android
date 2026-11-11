package com.pixelbot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.nio.ByteBuffer

class PixelBotScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
    }
    private var busy = false
    private var lastAnalysisAt = 0L
    private var lastSummary = ""
    private var lastSentAt = 0L

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "PixelBot screen analysis", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        thread = HandlerThread("PixelBotScreenAnalysis").also { it.start() }
        worker = Handler(thread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") (intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent)
        }
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("PixelBot анализирует экран")
            .setContentText("Кадры обрабатываются локально. Нажми «Остановить», чтобы завершить.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Остановить", android.app.PendingIntent.getService(
                this, 1, Intent(this, PixelBotScreenCaptureService::class.java).setAction(ACTION_STOP),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(FOREGROUND_ID, notification)

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, worker)
        startCapture()
        return START_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ source ->
            val now = System.currentTimeMillis()
            if (!busy && now - lastAnalysisAt >= 8000L) {
                val image = source.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()
                    if (bitmap != null) { lastAnalysisAt = now; analyze(bitmap) }
                }
            } else {
                source.acquireLatestImage()?.close()
            }
        }, worker)
        virtualDisplay = projection?.createVirtualDisplay(
            "PixelBotCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, worker
        )
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (padded != cropped) padded.recycle()
        if (cropped.width <= 640) return cropped
        val scaledHeight = (cropped.height * (640f / cropped.width)).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, 640, scaledHeight, true)
        if (scaled != cropped) cropped.recycle()
        return scaled
    }

    private fun analyze(bitmap: Bitmap) {
        busy = true
        val input = InputImage.fromBitmap(bitmap, 0)
        val textTask = recognizer.process(input)
        val labelTask = labeler.process(input)
        textTask.addOnSuccessListener { text ->
            val words = text.text.lowercase()
            val labels = mutableListOf<String>()
            labelTask.addOnSuccessListener { imageLabels ->
                labels.addAll(imageLabels.map { it.text.lowercase() })
                val summary = classify(words, labels)
                sendMood(summary)
                bitmap.recycle()
                busy = false
            }.addOnFailureListener {
                sendMood(classify(words, emptyList()))
                bitmap.recycle()
                busy = false
            }
        }.addOnFailureListener {
            labelTask.addOnCompleteListener {
                sendMood("activity")
                bitmap.recycle()
                busy = false
            }
        }
    }

    // OCR output is inspected only in memory for broad categories; raw text is never persisted or transmitted.
    private fun classify(text: String, labels: List<String>): String {
        val hints = (text + " " + labels.joinToString(" ")).lowercase()
        return when {
            listOf("pause", "play", "video", "youtube", "смотреть", "видео", "субтитр").any { it in hints } -> "video"
            listOf("game", "level", "score", "играть", "уровень", "счёт").any { it in hints } -> "game"
            listOf("search", "browser", "chrome", "поиск", "вкладка", "сайт").any { it in hints } -> "browse"
            listOf("settings", "настройки", "bluetooth", "wi-fi", "display").any { it in hints } -> "settings"
            listOf("chat", "message", "сообщение", "отправить").any { it in hints } -> "chat"
            labels.any { it in listOf("food", "drink", "menu", "plant", "animal", "nature") } -> "look"
            else -> "activity"
        }
    }

    private fun sendMood(mood: String) {
        val now = System.currentTimeMillis()
        if (mood == lastSummary && now - lastSentAt < 6000L) return
        lastSummary = mood
        lastSentAt = now
        startService(Intent(this, PixelBotOverlayService::class.java)
            .setAction(PixelBotOverlayService.ACTION_SCREEN_STATE)
            .putExtra(PixelBotOverlayService.EXTRA_MOOD, mood))
    }

    override fun onDestroy() {
        reader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        runCatching { recognizer.close() }
        runCatching { labeler.close() }
        thread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.pixelbot.STOP_CAPTURE"
        private const val CHANNEL = "pixelbot_capture"
        private const val FOREGROUND_ID = 207
    }
}