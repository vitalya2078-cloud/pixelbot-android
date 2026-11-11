package com.pixelbot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PixelBotAccessibilityService : AccessibilityService() {
    private var lastPackage = ""
    private var lastEventAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        val type = event.eventType
        if (pkg != lastPackage || now - lastEventAt > 1800L ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            lastPackage = pkg
            lastEventAt = now
            val mood = when (type) {
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scroll"
                AccessibilityEvent.TYPE_VIEW_CLICKED -> "click"
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "app"
                else -> "activity"
            }
            startService(android.content.Intent(this, PixelBotOverlayService::class.java)
                .setAction(PixelBotOverlayService.ACTION_ACTIVITY)
                .putExtra(PixelBotOverlayService.EXTRA_MOOD, mood))
        }
    }

    override fun onInterrupt() {}
}