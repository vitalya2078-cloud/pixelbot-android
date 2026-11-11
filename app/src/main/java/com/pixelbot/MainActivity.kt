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
            setPadding(32, 48, 32, 32)
        }
        layout.addView(TextView(this).apply {
            text = "PixelBot"
            textSize = 30f
        })
        layout.addView(TextView(this).apply {
            text = "A tiny companion for VK and Telegram notifications. PixelBot checks only which app sent a notification; it does not read message contents."
            textSize = 16f
            setPadding(0, 16, 0, 24)
        })
        layout.addView(Button(this).apply {
            text = "Allow display over other apps"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        layout.addView(Button(this).apply {
            text = "Enable notification access"
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        })
        layout.addView(Button(this).apply {
            text = "Show / hide mascot"
            setOnClickListener {
                val action = if (PixelBotOverlayService.isVisible) {
                    PixelBotOverlayService.ACTION_HIDE
                } else {
                    PixelBotOverlayService.ACTION_SHOW
                }
                startService(Intent(this, PixelBotOverlayService::class.java).setAction(action))
            }
        })
        setContentView(layout)
    }
}
