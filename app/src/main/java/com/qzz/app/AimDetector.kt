package com.qzz.app

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

object AimDetector {

    // quanto maior, mais perto da mira tem que tá o alvo
    private const val MAX_DIST_FROM_CENTER = 0.35f

    // tamanho mínimo do blob pra considerar inimigo (em pixels)
    private const val MIN_BLOB_SIZE = 30

    /**
     * Analisa o bitmap e move a mira pro alvo mais próximo do centro.
     * Detecção: pixels vermelhos (HUD nome/sangue de inimigo).
     */
    fun process(bmp: Bitmap, screenW: Int, screenH: Int) {
        val cx = screenW / 2f
        val cy = screenH / 2f
        val maxDist = screenW * MAX_DIST_FROM_CENTER

        var bestX = -1f
        var bestY = -1f
        var bestDist = Float.MAX_VALUE
        var hits = 0

        // varre a tela em passos de 4 px (mais rápido)
        val step = 4
        var y = 0
        while (y < screenH) {
            var x = 0
            while (x < screenW) {
                val px = bmp.getPixel(x, y)
                if (isRed(px)) {
                    val dx = x - cx
                    val dy = y - cy
                    val d = sqrt(dx * dx + dy * dy)
                    if (d < maxDist && d < bestDist) {
                        bestDist = d
                        bestX = x.toFloat()
                        bestY = y.toFloat()
                    }
                    hits++
                }
                x += step
            }
            y += step
        }

        // se achou poucos pixels vermelhos, ignora (ruído)
        if (hits < MIN_BLOB_SIZE) return
        if (bestX < 0f) return

        // move a mira pro alvo
        val svc = AimAccessibilityService.instance ?: return
        // desloca um pouco pra cima (headshot aproximado)
        svc.moveTouch(bestX, bestY - 40f)
    }

    /**
     * Detecção simples: vermelho dominante.
     * Ajusta os thresholds se tiver muito falso positivo.
     */
    private fun isRed(px: Int): Boolean {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        // vermelho forte: r alto, g e b baixos
        return r > 160 && g < 90 && b < 90
    }
}