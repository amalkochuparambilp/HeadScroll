package com.headscroller.headscroller.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class ScrollAccessibilityService : AccessibilityService() {

    private val scrollCooldownMs = 1200L
    private var lastScrollTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    private fun dispatchScrollGesture(direction: String): Boolean {
        val startY: Float
        val endY: Float
        when (direction) {
            ACTION_NEXT -> {
                startY = 0.9f
                endY = 0.2f
            }
            ACTION_PREV -> {
                startY = 0.2f
                endY = 0.9f
            }
            else -> return false
        }

        val now = System.currentTimeMillis()
        if (now - lastScrollTime < scrollCooldownMs) return false
        lastScrollTime = now

        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width / 2f, height * startY)
            lineTo(width / 2f, height * endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 300L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"

        @Volatile
        private var instance: ScrollAccessibilityService? = null

        fun dispatchScroll(direction: String): Boolean =
            instance?.dispatchScrollGesture(direction) ?: false
    }
}