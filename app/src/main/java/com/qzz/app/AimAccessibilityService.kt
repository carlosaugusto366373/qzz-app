package com.qzz.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class AimAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AimAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Move o toque na tela pra (x, y).
     * Só faz o toque, sem click.
     */
    fun moveTouch(x: Float, y: Float, durationMs: Long = 30) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Simula arrasto na tela (swipe).
     * Útil pra mover a mira com movimento contínuo.
     */
    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 60) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}