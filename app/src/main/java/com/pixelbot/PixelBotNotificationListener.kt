package com.pixelbot

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PixelBotNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = when (sbn.packageName) {
            "com.vkontakte.android" -> "vk"
            "org.telegram.messenger" -> "telegram"
            else -> return
        }

        // Deliberately do not inspect notification extras, title, sender, or message text.
        val intent = Intent(this, PixelBotOverlayService::class.java)
            .setAction(PixelBotOverlayService.ACTION_NOTIFICATION)
            .putExtra(PixelBotOverlayService.EXTRA_SOURCE, source)
        startService(intent)
    }
}
