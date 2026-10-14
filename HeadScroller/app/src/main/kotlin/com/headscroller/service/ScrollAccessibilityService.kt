package com.headscroller.headscroller.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScrollAccessibilityService : AccessibilityService() {

    private val SCROLL_COOLDOWN_MS = 1200
    private var lastScrollTime = 0L

    override fun onCreate() {
        super.onCreate()
        // Service is system-wide - starts when system binds to it
        Log.d("ScrollService", "ScrollAccessibilityService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ScrollService", "ScrollAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle system events if needed
    }

    override fun onInterrupt() {
        // Called when the accessibility service is interrupted
    }

    /**
     * Dispatch a swipe gesture to scroll through short videos.
     * Direction: "next" = swipe up (next short), "prev" = swipe down (previous short)
     */
    fun dispatchScroll(direction: String) {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < SCROLL_COOLDOWN_MS) return
        lastScrollTime = now

        // Get the active window's root node
        val source = rootNode
        if (source == null) return

        // Get screen dimensions to calculate swipe coordinates
        virtualDescendantIdMap.clear()
        findVirtualDescendants(source, 0)

        // Perform the appropriate swipe based on direction
        when (direction) {
            "next" -> performUpSwipe()
            "prev" -> performDownSwipe()
        }
    }

    private fun performUpSwipe() {
        // Swipe from bottom-center to top-center = next video
        swipe(
            startX = 0.5f,  // bottom center
            startY = 0.9f,  // near bottom
            endX = 0.5f,
            endY = 0.2f     // near top
        )
    }

    private fun performDownSwipe() {
        // Swipe from top-center to bottom-center = previous video
        swipe(
            startX = 0.5f,  // top center
            startY = 0.2f,  // near top
            endX = 0.5f,
            endY = 0.9f     // near bottom
        )
    }

    private fun swipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        // Get screen bounds
        val bounds = rootNode.bounds
        if (bounds == null) return

        val screenWidth = bounds.width()
        val screenHeight = bounds.height()

        // Calculate absolute coordinates
        val startXpx = (startX * screenWidth).toInt()
        val startYpx = (startY * screenHeight).toInt()
        val endXpx = (endX * screenWidth).toInt()
        val endYpx = (endY * screenHeight).toInt()

        // Create the swipe gesture
        val gesture = android.view.GestureDescription.View()
        val movement = android.view.MotionEventCompat.obtain(
            0,  // actionIndex
            0,  // actionMasked
            android.view.MotionEvent.ACTION_DOWN, startXpx, startYpx,
            0,  // buttonState
            1,  // edgeFlags
            1.0f,  // metaState
            0,  // orientation
            0,  // pointerId
            0   // source
        )

        // Add up event
        val upEvent = android.view.MotionEventCompat.obtain(
            0,
            0,
            android.view.MotionEvent.ACTION_UP,
            endXpx, endYpx,
            0,
            0,
            1.0f,
            0,
            1,
            0
        )

        gesture.addMovement(movement)
        gesture.addMovement(upEvent)

        // Post the gesture description
        onAccessibilityEvent(AccessibilityEvent.obtain(
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            source = null
        ))
    }

    /**
     * Find virtual descendants to get screen dimensions
     */
    private fun findVirtualDescendants(node: AccessibilityNodeInfo, depth: Int) {
        if (node == null) return
        if (node.isFocusable || node.isClickable || node.isFocused) {
            // Log node info for debugging
        }
        virtualDescendantIdMap[node] = depth
        for (i in 0 until node.childCount) {
            findVirtualDescendants(node.getChildAt(i), depth + 1)
        }
    }

    // Helper to perform global swipe using accessibility
    private fun performGlobalSwipe(direction: Int) {
        // Use performGlobalAction for system-level gestures
        // But this is deprecated in favor of dispatchGesture on newer API levels
        // For broader compatibility, we'll use the manual swipe approach above
        performGlobalAction(direction) {
            // fallback if needed
        }
    }

    // Map to track virtual view IDs
    private val virtualDescendantIdMap = mutableMapOf<AccessibilityNodeInfo, Int>()

    companion object {
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
    }
}