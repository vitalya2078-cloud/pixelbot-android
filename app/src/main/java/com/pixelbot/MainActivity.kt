package com.pixelbot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val captureRequest = 7401

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
        }
        layout.addView(TextView(this).apply {
            text = "PixelBot"
            textSize = 32f
            setPadding(0, 0, 0, 8)
        })
        layout.addView(TextView(this).apply {
            text = "Экранный компаньон: наблюдает за активностью приложений, анализирует изображение экрана и распознаёт текст локально на устройстве. Кадры и распознанный текст не отправляются на сервер и не сохраняются."
            textSize = 15f
            setPadding(0, 0, 0, 20)
        })
        button(layout, "1. Разрешить показ поверх окон") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button(layout, "2. Включить события экрана (Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button(layout, "3. Запустить анализ экрана") {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
        }
        button(layout, "Показать / скрыть маскота") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                val action = if (PixelBotOverlayService.isVisible) PixelBotOverlayService.ACTION_HIDE else PixelBotOverlayService.ACTION_SHOW
                startService(Intent(this, PixelBotOverlayService::class.java).setAction(action))
            }
        }
        layout.addView(TextView(this).apply {
            text = "Важно: Android отдельно спросит разрешение на захват экрана и покажет системный индикатор. Остановить анализ можно в уведомлении PixelBot. Не запускай захват на экранах с паролями, банковскими данными или другой конфиденциальной информацией."
            textSize = 13f
            setPadding(0, 22, 0, 0)
        })
        setContentView(layout)
    }

    private fun button(layout: LinearLayout, label: String, action: () -> Unit) {
        layout.addView(Button(this).apply { text = label; setOnClickListener { action() } })
    }

    @Deprecated("Deprecated in Android API, retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, PixelBotScreenCaptureService::class.java)
                .putExtra(PixelBotScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(PixelBotScreenCaptureService.EXTRA_RESULT_DATA, data)
            startForegroundService(service)
            startService(Intent(this, PixelBotOverlayService::class.java).setAction(PixelBotOverlayService.ACTION_SHOW))
        }
    }
}