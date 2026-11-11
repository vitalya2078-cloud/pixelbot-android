package com.pixelbot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 28)
        }
        layout.addView(TextView(this).apply {
            text = "PixelBot"
            textSize = 32f
            setPadding(0, 0, 0, 8)
        })
        layout.addView(TextView(this).apply {
            text = "Компаньон, который реагирует на уведомления из любых приложений. PixelBot определяет только приложение-источник и не читает текст уведомлений, имена отправителей или содержимое сообщений."
            textSize = 16f
            setPadding(0, 0, 0, 20)
        })
        layout.addView(TextView(this).apply {
            text = "1. Разреши показ поверх других приложений.\n2. Если Android заблокировал доступ к уведомлениям: открой сведения о PixelBot → меню ⋮ → «Разрешить ограниченные настройки».\n3. Вернись в доступ к уведомлениям и включи PixelBot.\n\nЧтобы реагировать на уведомления всех приложений, включи PixelBot в системном списке доступа к уведомлениям."
            textSize = 14f
            setPadding(0, 0, 0, 18)
        })
        layout.addView(Button(this).apply {
            text = "Открыть настройки PixelBot"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })
        layout.addView(Button(this).apply {
            text = "Разрешить показ поверх окон"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        layout.addView(Button(this).apply {
            text = "Открыть доступ к уведомлениям"
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        })
        layout.addView(Button(this).apply {
            text = "Показать / скрыть маскота"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    return@setOnClickListener
                }
                val action = if (PixelBotOverlayService.isVisible) PixelBotOverlayService.ACTION_HIDE else PixelBotOverlayService.ACTION_SHOW
                val serviceIntent = Intent(this@MainActivity, PixelBotOverlayService::class.java).apply { this.action = action }
                startService(serviceIntent)
            }
        })
        setContentView(layout)
    }
}
