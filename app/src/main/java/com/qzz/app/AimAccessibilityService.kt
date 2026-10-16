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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // não faz nada — só precisa estar conectado
    }

    override fun onInterrupt() {
        // obrigatório, mas não usado
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Move o toque na tela pra (x, y).
     * Chamado pelo AimLoop quando encontra um alvo.
     */
    fun moveTouch(x: Float, y: Float, durationMs: Long = 50) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}