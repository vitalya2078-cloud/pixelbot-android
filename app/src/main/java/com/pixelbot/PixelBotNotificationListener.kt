package com.pixelbot

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PixelBotNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val source = when (sbn.packageName) {
            "com.vkontakte.android" -> "vk"
            "org.telegram.messenger" -> "telegram"
            else -> "other"
        }
        runCatching {
            startService(Intent(this, PixelBotOverlayService::class.java)
                .setAction(PixelBotOverlayService.ACTION_NOTIFICATION)
                .putExtra(PixelBotOverlayService.EXTRA_SOURCE, source))
        }
    }
}