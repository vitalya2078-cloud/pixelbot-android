package com.pixelbot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PixelBotAccessibilityService : AccessibilityService() {
    private var lastPackage = ""
    private var lastMood = ""
    private var lastEventAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        activePackage = pkg
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        val type = event.eventType
        val important = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (!important && pkg == lastPackage && now - lastEventAt < 1400L) return
        val text = collectVisibleText(rootInActiveWindow)
        val mood = classify(pkg, type, text)
        val shouldSend = mood != lastMood || important || now - lastEventAt > 2500L
        lastPackage = pkg
        lastMood = mood
        lastEventAt = now
        if (shouldSend) startService(Intent(this, PixelBotOverlayService::class.java)
            .setAction(PixelBotOverlayService.ACTION_ACTIVITY)
            .putExtra(PixelBotOverlayService.EXTRA_MOOD, mood))
    }

    // Text is inspected only in memory. Password fields are skipped and no text is stored.
    private fun collectVisibleText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val out = StringBuilder()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 14 || out.length >= 2200 || node.isPassword) return
            if (node.isVisibleToUser) {
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append(' ') }
                node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append(' ') }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return out.toString().lowercase()
    }

    private fun classify(pkg: String, type: Int, text: String): String = when {
        pkg.contains("youtube") || pkg.contains("video") ||
            listOf("видео", "субтитр", "pause", "play", "воспроизведение").any { it in text } -> "video"
        pkg.contains("game") ||
            listOf("играть", "уровень", "счёт", "score", "level", "play now").any { it in text } -> "game"
        pkg.contains("chrome") || pkg.contains("browser") ||
            listOf("поиск", "адресная строка", "вкладка", "search", "website").any { it in text } -> "browse"
        listOf("настройки", "settings", "bluetooth", "wi-fi", "яркость", "display").any { it in text } -> "settings"
        listOf("сообщение", "отправить", "чат", "message", "send", "reply").any { it in text } -> "chat"
        type == AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scroll"
        type == AccessibilityEvent.TYPE_VIEW_CLICKED -> "click"
        type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "app"
        else -> "activity"
    }

    override fun onInterrupt() {}

    companion object {
        @Volatile var activePackage: String = ""
            private set
    }
}