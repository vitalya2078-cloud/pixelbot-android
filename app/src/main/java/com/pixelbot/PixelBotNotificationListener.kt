package com.pixelbot

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class PixelBotNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // React to every app, except PixelBot itself. Only the package identifier is inspected.
        // Never read extras, title, sender, ticker, or notification/message text.
        if (sbn.packageName == packageName) return

        val source = when (sbn.packageName) {
            "com.vkontakte.android" -> "vk"
            "org.telegram.messenger" -> "telegram"
            else -> "other"
        }

        val intent = Intent(this, PixelBotOverlayService::class.java)
            .setAction(PixelBotOverlayService.ACTION_NOTIFICATION)
            .putExtra(PixelBotOverlayService.EXTRA_SOURCE, source)
        runCatching { startService(intent) }
    }
}
