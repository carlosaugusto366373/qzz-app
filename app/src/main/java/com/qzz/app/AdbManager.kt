package com.qzz.app

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object AdbManager {
    private const val PERMISSION_REQUEST_CODE = 100

    fun checkPermission(onResult: (Boolean) -> Unit) {
        if (!Shizuku.pingBinder()) {
            onResult(false)
            return
        }
        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
        } else {
            onResult(false)
        }
    }

    fun requestPermission() {
        if (!Shizuku.isPreV11() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    // Roda o comando usando ProcessBuilder padrão
    fun executeCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.ifEmpty { "OK" }
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 100) {
        executeCommand("input swipe $x1 $y1 $x2 $y2 $duration")
    }

    fun tap(x: Int, y: Int) {
        executeCommand("input tap $x $y")
    }
}